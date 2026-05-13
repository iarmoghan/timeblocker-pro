package ie.ncirl.timeblocker.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.domain.Block;
import ie.ncirl.timeblocker.service.PlanService;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestParam("weekStart") String weekStart) {
        try {
            LocalDate ws = LocalDate.parse(weekStart);
            var result = planService.generateWeeklyPlan(ws);
            List<Block> blocks = planService.getBlocks(result.planId());

            return ResponseEntity.ok(Map.of(
                    "planId", result.planId(),
                    "weekStart", result.weekStart().toString(),
                    "scheduledBlocks", result.scheduledBlocks(),
                    "unscheduledTasks", result.unscheduledTasks(),
                    "unscheduled", result.unscheduled(),
                    "blocks", blocks
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Plan generation error: " + e.getMessage());
        }
    }

    @GetMapping("/{planId}/blocks")
    public List<Block> blocks(@PathVariable Long planId) {
        return planService.getBlocks(planId);
    }
}