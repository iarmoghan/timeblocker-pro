package ie.ncirl.timeblocker.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.ncirl.timeblocker.domain.Block;
import ie.ncirl.timeblocker.domain.Plan;
import ie.ncirl.timeblocker.domain.Task;
import ie.ncirl.timeblocker.domain.UserPreferences;
import ie.ncirl.timeblocker.repo.BlockRepository;
import ie.ncirl.timeblocker.repo.EventRepository;
import ie.ncirl.timeblocker.repo.PlanRepository;
import ie.ncirl.timeblocker.repo.TaskRepository;
import ie.ncirl.timeblocker.repo.UserPreferencesRepository;

@Service
public class PlanService {

    // The planner uses Dublin time because this project is based around an Irish student timetable.
    private static final ZoneId ZONE = ZoneId.of("Europe/Dublin");

    // This project currently uses one demo user.
    // Full login and multi-user support would be future work.
    private static final Long DEMO_USER_ID = 1L;

    // Repositories are used by the service to read and write data from the database.
    private final PlanRepository planRepo;
    private final BlockRepository blockRepo;
    private final TaskRepository taskRepo;
    private final EventRepository eventRepo;
    private final UserPreferencesRepository prefsRepo;

    public PlanService(
            PlanRepository planRepo,
            BlockRepository blockRepo,
            TaskRepository taskRepo,
            EventRepository eventRepo,
            UserPreferencesRepository prefsRepo
    ) {
        this.planRepo = planRepo;
        this.blockRepo = blockRepo;
        this.taskRepo = taskRepo;
        this.eventRepo = eventRepo;
        this.prefsRepo = prefsRepo;
    }

    // This record is returned when a task cannot fully fit into the available study time.
    // It lets the frontend show which task still has remaining minutes.
    public record UnscheduledTask(Long taskId, String title, int remainingMinutes) {}

    // This is the summary returned after generating or replanning a week.
    // It contains the plan id, week start, number of created blocks and unscheduled work.
    public record GenerateResult(
            Long planId,
            LocalDate weekStart,
            int scheduledBlocks,
            int unscheduledTasks,
            List<UnscheduledTask> unscheduled
    ) {}

    // Internal helper record used to represent a time range.
    // I use this for both busy intervals and free intervals.
    private record Interval(Instant start, Instant end) {}

    // Loads the user's study preferences.
    // If no preferences exist yet, it creates default preferences for the demo user.
    private UserPreferences loadPrefs() {
        return prefsRepo.findById(DEMO_USER_ID).orElseGet(() -> {
            UserPreferences p = new UserPreferences();
            p.setUserId(DEMO_USER_ID);
            return prefsRepo.save(p);
        });
    }

    // This method is called when the user clicks "Generate Plan".
    // It creates or finds the weekly plan, clears old generated blocks,
    // and then schedules open tasks into available time.
    @Transactional
    public GenerateResult generateWeeklyPlan(LocalDate weekStart) {
        Plan plan = planRepo.findByUserIdAndWeekStart(DEMO_USER_ID, weekStart).orElseGet(() -> {
            Plan p = new Plan();
            p.setUserId(DEMO_USER_ID);
            p.setWeekStart(weekStart);
            return planRepo.save(p);
        });

        // Generate means refresh the week, so old blocks for that plan are removed first.
        blockRepo.deleteByPlanId(plan.getId());

        // Instant.MIN means scheduling can start from the beginning of the selected week.
        // No blocks need to be kept busy during a fresh generate.
        return scheduleIntoPlan(plan, weekStart, Instant.MIN, List.of());
    }

    // This method is called when the user clicks "Replan".
    // Replanning keeps completed work fixed and regenerates future planned work.
    @Transactional
    public GenerateResult replanWeek(LocalDate weekStart) {
        Plan plan = planRepo.findByUserIdAndWeekStart(DEMO_USER_ID, weekStart)
                .orElseThrow(() -> new IllegalArgumentException("No plan exists for that week. Generate first."));

        Instant now = Instant.now();

        List<Block> all = blockRepo.findByPlanIdOrderByStartTimeAsc(plan.getId());
        List<Block> keepBusy = new ArrayList<>();

        for (Block b : all) {
            boolean done = "DONE".equalsIgnoreCase(b.getStatus());
            boolean planned = "PLANNED".equalsIgnoreCase(b.getStatus());

            if (done) {
                // DONE blocks are kept because they represent work already completed.
                // During replanning, these blocks are treated like busy time.
                keepBusy.add(b);
            } else if (planned && !b.getEndTime().isBefore(now)) {
                // Future PLANNED blocks are deleted so they can be regenerated.
                blockRepo.deleteById(b.getId());
            }
        }

        // Re-schedule remaining open work from the current time onwards.
        return scheduleIntoPlan(plan, weekStart, now, keepBusy);
    }

    // Returns all blocks for a plan in time order.
    // The controller uses this to send blocks back to the frontend.
    public List<Block> getBlocks(Long planId) {
        return blockRepo.findByPlanIdOrderByStartTimeAsc(planId);
    }

    // This is the main scheduling method.
    // It calculates free windows and places open tasks into those windows.
    private GenerateResult scheduleIntoPlan(Plan plan, LocalDate weekStart, Instant fromInstant, List<Block> keepBusyBlocks) {
        UserPreferences prefs = loadPrefs();

        // Load preferences, with safe defaults if something is missing.
        int dayStartHour = prefs.getDayStartHour() == null ? 8 : prefs.getDayStartHour();
        int dayEndHour = prefs.getDayEndHour() == null ? 20 : prefs.getDayEndHour();
        int blockMin = prefs.getBlockMinutes() == null ? 60 : prefs.getBlockMinutes();

        // Basic safety checks so invalid preferences do not break the planner.
        if (dayEndHour <= dayStartHour) {
            dayStartHour = 8;
            dayEndHour = 20;
        }

        if (blockMin < 15) {
            blockMin = 15;
        }

        if (blockMin > 240) {
            blockMin = 240;
        }

        // Convert the selected week into an Instant range.
        // This lets the code compare events and blocks using exact timestamps.
        Instant weekStartInstant = weekStart.atStartOfDay(ZONE).toInstant();
        Instant weekEndInstant = weekStart.plusDays(7).atStartOfDay(ZONE).toInstant();

        // Load imported timetable events that overlap the selected week.
        // These events are treated as fixed busy time.
        var events = eventRepo.findAllByOrderByStartTimeAsc().stream()
                .filter(e -> e.getStartTime().isBefore(weekEndInstant) && e.getEndTime().isAfter(weekStartInstant))
                .collect(Collectors.toList());

        List<Interval> busy = new ArrayList<>();

        // Add timetable events to the busy list.
        for (var e : events) {
            busy.add(new Interval(e.getStartTime(), e.getEndTime()));
        }

        // Add completed blocks to busy time during replanning.
        // This prevents the planner from overwriting completed study sessions.
        for (Block b : keepBusyBlocks) {
            busy.add(new Interval(b.getStartTime(), b.getEndTime()));
        }

        busy.sort(Comparator.comparing(Interval::start));

        List<Interval> free = new ArrayList<>();

        // Build free study windows for each day of the selected week.
        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);

            // Base study window for the day, based on preferences.
            Instant baseStart = day.atTime(dayStartHour, 0).atZone(ZONE).toInstant();
            Instant baseEnd = day.atTime(dayEndHour, 0).atZone(ZONE).toInstant();

            // If the whole day is already before the replan time, skip it.
            if (baseEnd.isBefore(fromInstant)) {
                continue;
            }

            Instant ds = baseStart;

            // During replanning, do not schedule before the current time.
            if (ds.isBefore(fromInstant)) {
                ds = fromInstant.truncatedTo(ChronoUnit.MINUTES);
            }

            Instant de = baseEnd;

            if (!ds.isBefore(de)) {
                continue;
            }

            final Instant dayStartFinal = ds;
            final Instant dayEndFinal = de;

            List<Interval> dayBusy = new ArrayList<>();

            // Only take busy intervals that overlap this day.
            for (Interval b : busy) {
                if (b.start().isBefore(dayEndFinal) && b.end().isAfter(dayStartFinal)) {
                    dayBusy.add(new Interval(max(b.start(), dayStartFinal), min(b.end(), dayEndFinal)));
                }
            }

            // Subtract busy intervals from the study window.
            // The result is a list of free windows where study blocks can be placed.
            free.addAll(subtract(new Interval(dayStartFinal, dayEndFinal), dayBusy));
        }

        /*
         * Task ordering strategy:
         * 1. Earliest deadline first
         * 2. Highest priority second
         * 3. Oldest created task third
         * 4. Lowest id last as a stable fallback
         *
         * 
         */
        List<Task> tasks = taskRepo.findByUserIdAndStatusOrderByDeadlineAsc(DEMO_USER_ID, "OPEN")
                .stream()
                .sorted(Comparator
                        .comparing((Task t) -> t.getDeadline() == null ? Instant.MAX : t.getDeadline())
                        .thenComparing((Task t) -> -(t.getPriority() == null ? 0 : t.getPriority()))
                        .thenComparing((Task t) -> t.getCreatedAt() == null ? Instant.MAX : t.getCreatedAt())
                        .thenComparing((Task t) -> t.getId() == null ? Long.MAX_VALUE : t.getId()))
                .toList();

        Map<Long, Integer> doneMinutesByTask = new HashMap<>();

        // During replanning, calculate how many minutes are already completed for each task.
        // This means the planner only schedules the remaining work.
        for (Block b : keepBusyBlocks) {
            if (b.getTaskId() != null) {
                long mins = Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
                doneMinutesByTask.merge(b.getTaskId(), (int) mins, Integer::sum);
            }
        }

        int blocksCreated = 0;
        List<UnscheduledTask> unscheduled = new ArrayList<>();

        // Try to schedule each open task.
        for (Task task : tasks) {
            int est = task.getEstMinutes() == null ? blockMin : task.getEstMinutes();
            int alreadyDone = doneMinutesByTask.getOrDefault(task.getId(), 0);
            int remaining = Math.max(0, est - alreadyDone);

            // If the task is already fully completed, do not schedule it again.
            if (remaining == 0) {
                continue;
            }

            Instant deadline = task.getDeadline();

            // Keep creating blocks until the task is fully scheduled
            // or until there is no suitable free slot left.
            while (remaining > 0) {
                int slice = Math.min(blockMin, remaining);

                // Find the first free slot that can fit this slice.
                // Deadline is also checked inside findFirstFit().
                int idx = findFirstFit(free, slice, deadline);

                if (idx < 0) {
                    break;
                }

                Interval slot = free.get(idx);
                Instant start = slot.start();
                Instant end = start.plus(Duration.ofMinutes(slice));

                // Create and save the planned study block.
                Block b = new Block();
                b.setPlanId(plan.getId());
                b.setTaskId(task.getId());
                b.setTitle(task.getTitle());
                b.setStartTime(start);
                b.setEndTime(end);
                b.setStatus("PLANNED");
                blockRepo.save(b);

                blocksCreated++;
                remaining -= slice;

                // Remove the used part of the free slot.
                // If there is leftover time after the new block, keep it as a smaller free window.
                Interval leftover = new Interval(end, slot.end());
                free.remove(idx);

                if (leftover.start().isBefore(leftover.end())) {
                    free.add(idx, leftover);
                }
            }

            // If the task still has remaining minutes, tell the frontend what could not fit.
            if (remaining > 0) {
                unscheduled.add(new UnscheduledTask(task.getId(), task.getTitle(), remaining));
            }
        }

        return new GenerateResult(plan.getId(), weekStart, blocksCreated, unscheduled.size(), unscheduled);
    }

    // Finds the first free interval that can fit a block of the given length.
    // If a task has a deadline, the block must end before that deadline.
    private static int findFirstFit(List<Interval> free, int minutes, Instant deadline) {
        Duration dur = Duration.ofMinutes(minutes);

        for (int i = 0; i < free.size(); i++) {
            Interval slot = free.get(i);
            Instant end = slot.start().plus(dur);

            // Skip this slot if the block would not fit inside it.
            if (end.isAfter(slot.end())) {
                continue;
            }

            // Skip this slot if it would finish after the task deadline.
            if (deadline != null && end.isAfter(deadline)) {
                continue;
            }

            return i;
        }

        // -1 means no suitable slot was found.
        return -1;
    }

    // Subtracts busy intervals from one study window.
    // Example:
    // Window: 09:00-18:00
    // Busy:  10:00-11:00
    // Free:  09:00-10:00 and 11:00-18:00
    private static List<Interval> subtract(Interval window, List<Interval> busy) {
        List<Interval> free = new ArrayList<>();
        Instant cursor = window.start();

        busy.sort(Comparator.comparing(Interval::start));

        for (Interval b : busy) {
            if (b.start().isAfter(cursor)) {
                free.add(new Interval(cursor, b.start()));
            }

            cursor = max(cursor, b.end());
        }

        if (cursor.isBefore(window.end())) {
            free.add(new Interval(cursor, window.end()));
        }

        // Ignore very tiny gaps because they are not useful for study blocks.
        return free.stream()
                .filter(i -> Duration.between(i.start(), i.end()).toMinutes() >= 10)
                .toList();
    }

    // Returns the later of two Instants.
    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    // Returns the earlier of two Instants.
    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}