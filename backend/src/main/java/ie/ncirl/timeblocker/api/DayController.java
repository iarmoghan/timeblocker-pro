package ie.ncirl.timeblocker.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.domain.Block;
import ie.ncirl.timeblocker.domain.Event;
import ie.ncirl.timeblocker.repo.BlockRepository;
import ie.ncirl.timeblocker.repo.EventRepository;
import ie.ncirl.timeblocker.repo.PlanRepository;
import ie.ncirl.timeblocker.repo.TaskRepository;

@RestController
@RequestMapping("/api/day")
public class DayController {

    private static final ZoneId ZONE = ZoneId.of("Europe/Dublin");
    private static final Long DEMO_USER_ID = 1L;

    private final EventRepository eventRepo;
    private final PlanRepository planRepo;
    private final BlockRepository blockRepo;
    private final TaskRepository taskRepo;

    public DayController(EventRepository eventRepo, PlanRepository planRepo, BlockRepository blockRepo, TaskRepository taskRepo) {
        this.eventRepo = eventRepo;
        this.planRepo = planRepo;
        this.blockRepo = blockRepo;
        this.taskRepo = taskRepo;
    }

    @GetMapping
    public Map<String, Object> getDay(@RequestParam("date") String date) {
        LocalDate d = LocalDate.parse(date);

        LocalDate weekStart = toMonday(d);

        Instant dayStart = d.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = d.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Event> events = eventRepo.findByStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(dayEnd, dayStart);

        var planOpt = planRepo.findByUserIdAndWeekStart(DEMO_USER_ID, weekStart);

        List<Block> blocks = planOpt
                .map(p -> blockRepo.findByPlanIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(p.getId(), dayEnd, dayStart))
                .orElse(List.of());

        int openTasksCount = taskRepo.findByUserIdAndStatusOrderByDeadlineAsc(DEMO_USER_ID, "OPEN").size();

        return Map.of(
                "date", d.toString(),
                "weekStart", weekStart.toString(),
                "hasPlan", planOpt.isPresent(),
                "openTasksCount", openTasksCount,
                "events", events,
                "blocks", blocks
        );
    }

    private static LocalDate toMonday(LocalDate date) {
        int dow = date.getDayOfWeek().getValue(); // Mon=1..Sun=7
        return date.minusDays(dow - 1L);
    }
}
