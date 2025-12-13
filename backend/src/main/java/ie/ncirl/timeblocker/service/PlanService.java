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

    private static final ZoneId ZONE = ZoneId.of("Europe/Dublin");
    private static final Long DEMO_USER_ID = 1L;

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

    public record GenerateResult(Long planId, LocalDate weekStart, int scheduledBlocks, int unscheduledTasks) {}

    private record Interval(Instant start, Instant end) {}

    private UserPreferences loadPrefs() {
        return prefsRepo.findById(DEMO_USER_ID).orElseGet(() -> {
            UserPreferences p = new UserPreferences();
            p.setUserId(DEMO_USER_ID);
            return prefsRepo.save(p);
        });
    }

    @Transactional
    public GenerateResult generateWeeklyPlan(LocalDate weekStart) {
        Plan plan = planRepo.findByUserIdAndWeekStart(DEMO_USER_ID, weekStart).orElseGet(() -> {
            Plan p = new Plan();
            p.setUserId(DEMO_USER_ID);
            p.setWeekStart(weekStart);
            return planRepo.save(p);
        });

        // Fresh generation: wipe blocks for this plan
        blockRepo.deleteByPlanId(plan.getId());

        return scheduleIntoPlan(plan, weekStart, Instant.MIN, List.of());
    }

    @Transactional
    public GenerateResult replanWeek(LocalDate weekStart) {
        Plan plan = planRepo.findByUserIdAndWeekStart(DEMO_USER_ID, weekStart)
                .orElseThrow(() -> new IllegalArgumentException("No plan exists for that week. Generate first."));

        Instant now = Instant.now();

        // Keep DONE blocks as busy, delete future PLANNED blocks
        List<Block> all = blockRepo.findByPlanIdOrderByStartTimeAsc(plan.getId());
        List<Block> keepBusy = new ArrayList<>();

        for (Block b : all) {
            boolean done = "DONE".equalsIgnoreCase(b.getStatus());
            boolean planned = "PLANNED".equalsIgnoreCase(b.getStatus());

            if (done) {
                keepBusy.add(b);
            } else if (planned && !b.getEndTime().isBefore(now)) {
                blockRepo.deleteById(b.getId());
            }
        }

        return scheduleIntoPlan(plan, weekStart, now, keepBusy);
    }

    public List<Block> getBlocks(Long planId) {
        return blockRepo.findByPlanIdOrderByStartTimeAsc(planId);
    }

    private GenerateResult scheduleIntoPlan(Plan plan, LocalDate weekStart, Instant fromInstant, List<Block> keepBusyBlocks) {
        UserPreferences prefs = loadPrefs();

        int dayStartHour = prefs.getDayStartHour() == null ? 8 : prefs.getDayStartHour();
        int dayEndHour   = prefs.getDayEndHour() == null ? 20 : prefs.getDayEndHour();
        int blockMin     = prefs.getBlockMinutes() == null ? 60 : prefs.getBlockMinutes();

        if (dayEndHour <= dayStartHour) { dayStartHour = 8; dayEndHour = 20; }
        if (blockMin < 15) blockMin = 15;

        Instant weekStartInstant = weekStart.atStartOfDay(ZONE).toInstant();
        Instant weekEndInstant = weekStart.plusDays(7).atStartOfDay(ZONE).toInstant();

        // Busy intervals from timetable events
        var events = eventRepo.findAllByOrderByStartTimeAsc().stream()
                .filter(e -> e.getStartTime().isBefore(weekEndInstant) && e.getEndTime().isAfter(weekStartInstant))
                .collect(Collectors.toList());

        List<Interval> busy = new ArrayList<>();
        for (var e : events) busy.add(new Interval(e.getStartTime(), e.getEndTime()));

        // Busy intervals from DONE blocks we keep
        for (Block b : keepBusyBlocks) {
            busy.add(new Interval(b.getStartTime(), b.getEndTime()));
        }

        busy.sort(Comparator.comparing(Interval::start));

        // Build free intervals day-by-day
        List<Interval> free = new ArrayList<>();

        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);

            Instant baseStart = day.atTime(dayStartHour, 0).atZone(ZONE).toInstant();
            Instant baseEnd   = day.atTime(dayEndHour, 0).atZone(ZONE).toInstant();

            // Don’t schedule in the past (for replan)
            if (baseEnd.isBefore(fromInstant)) continue;

            Instant ds = baseStart;
            if (ds.isBefore(fromInstant)) {
                ds = fromInstant.truncatedTo(ChronoUnit.MINUTES);
            }
            Instant de = baseEnd;

            if (!ds.isBefore(de)) continue;

            final Instant dayStartFinal = ds;
            final Instant dayEndFinal = de;

            // Collect busy intervals that overlap this day window
            List<Interval> dayBusy = new ArrayList<>();
            for (Interval b : busy) {
                if (b.start().isBefore(dayEndFinal) && b.end().isAfter(dayStartFinal)) {
                    dayBusy.add(new Interval(max(b.start(), dayStartFinal), min(b.end(), dayEndFinal)));
                }
            }

            free.addAll(subtract(new Interval(dayStartFinal, dayEndFinal), dayBusy));
        }

        // OPEN tasks only, sorted by deadline then priority
        List<Task> tasks = taskRepo.findByUserIdAndStatusOrderByDeadlineAsc(DEMO_USER_ID, "OPEN")
                .stream()
                .sorted(Comparator
                        .comparing((Task t) -> t.getDeadline() == null ? Instant.MAX : t.getDeadline())
                        .thenComparing((Task t) -> -t.getPriority()))
                .toList();

        // Minutes already completed from DONE blocks
        Map<Long, Integer> doneMinutesByTask = new HashMap<>();
        for (Block b : keepBusyBlocks) {
            if (b.getTaskId() != null) {
                long mins = Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
                doneMinutesByTask.merge(b.getTaskId(), (int) mins, Integer::sum);
            }
        }

        int blocksCreated = 0;
        int unscheduledTasks = 0;

        for (Task task : tasks) {
            int est = task.getEstMinutes() == null ? blockMin : task.getEstMinutes();
            int alreadyDone = doneMinutesByTask.getOrDefault(task.getId(), 0);
            int remaining = Math.max(0, est - alreadyDone);

            if (remaining == 0) continue;

            Instant deadline = task.getDeadline();
            boolean scheduledAny = false;

            while (remaining > 0) {
                int slice = Math.min(blockMin, remaining);

                int idx = findFirstFit(free, slice, deadline);
                if (idx < 0) break;

                Interval slot = free.get(idx);
                Instant start = slot.start();
                Instant end = start.plus(Duration.ofMinutes(slice));

                Block b = new Block();
                b.setPlanId(plan.getId());
                b.setTaskId(task.getId());
                b.setTitle(task.getTitle());
                b.setStartTime(start);
                b.setEndTime(end);
                b.setStatus("PLANNED");
                blockRepo.save(b);

                blocksCreated++;
                scheduledAny = true;
                remaining -= slice;

                // Consume from start of that free interval
                Interval leftover = new Interval(end, slot.end());
                free.remove(idx);
                if (leftover.start().isBefore(leftover.end())) {
                    free.add(idx, leftover);
                }
            }

            if (!scheduledAny) unscheduledTasks++;
        }

        return new GenerateResult(plan.getId(), weekStart, blocksCreated, unscheduledTasks);
    }

    private static int findFirstFit(List<Interval> free, int minutes, Instant deadline) {
        Duration dur = Duration.ofMinutes(minutes);
        for (int i = 0; i < free.size(); i++) {
            Interval slot = free.get(i);
            Instant end = slot.start().plus(dur);
            if (end.isAfter(slot.end())) continue;
            if (deadline != null && end.isAfter(deadline)) continue;
            return i;
        }
        return -1;
    }

    private static List<Interval> subtract(Interval window, List<Interval> busy) {
        List<Interval> free = new ArrayList<>();
        Instant cursor = window.start();

        // busy is assumed sorted or small; either way works
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

        // Remove tiny fragments (<10 min)
        return free.stream()
                .filter(i -> Duration.between(i.start(), i.end()).toMinutes() >= 10)
                .toList();
    }

    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
}
