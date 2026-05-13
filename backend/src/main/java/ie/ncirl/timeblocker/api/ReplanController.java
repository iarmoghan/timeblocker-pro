package ie.ncirl.timeblocker.api;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.service.PlanService;

@RestController
@RequestMapping("/api/replan")
public class ReplanController {

    private final PlanService planService;

    public ReplanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ResponseEntity<?> replan(@RequestParam("weekStart") String weekStart) {
        try {
            LocalDate ws = LocalDate.parse(weekStart);
            var r = planService.replanWeek(ws);

            return ResponseEntity.ok(Map.of(
                    "planId", r.planId(),
                    "weekStart", r.weekStart().toString(),
                    "scheduledBlocks", r.scheduledBlocks(),
                    "unscheduledTasks", r.unscheduledTasks(),
                    "unscheduled", r.unscheduled(),
                    "blocks", planService.getBlocks(r.planId())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Replan error: " + e.getMessage());
        }
    }
}