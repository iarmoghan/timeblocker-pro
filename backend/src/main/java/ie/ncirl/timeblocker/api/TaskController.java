package ie.ncirl.timeblocker.api;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.domain.Block;
import ie.ncirl.timeblocker.domain.Task;
import ie.ncirl.timeblocker.repo.BlockRepository;
import ie.ncirl.timeblocker.repo.TaskRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository taskRepo;
    private final BlockRepository blockRepo;
    private static final Long DEMO_USER_ID = 1L;

    public TaskController(TaskRepository taskRepo, BlockRepository blockRepo) {
        this.taskRepo = taskRepo;
        this.blockRepo = blockRepo;
    }

    public record CreateTaskRequest(
            @NotBlank String title,
            Instant deadline,
            @Min(1) Integer estMinutes,
            Integer priority
    ) {}

    @GetMapping
    public List<Task> listAll() {
        return taskRepo.findByUserIdOrderByStatusAscDeadlineAsc(DEMO_USER_ID);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateTaskRequest req) {
        if (req.title() == null || req.title().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Task title is required.");
        }

        int estMinutes = req.estMinutes() == null ? 60 : req.estMinutes();

        if (estMinutes < 1) {
            return ResponseEntity.badRequest().body("Estimate minutes must be at least 1.");
        }

        int priority = req.priority() == null ? 0 : req.priority();

        if (priority < 0 || priority > 5) {
            return ResponseEntity.badRequest().body("Priority must be between 0 and 5.");
        }

        Task t = new Task();
        t.setUserId(DEMO_USER_ID);
        t.setTitle(req.title().trim());
        t.setDeadline(req.deadline());
        t.setEstMinutes(estMinutes);
        t.setPriority(priority);
        t.setStatus("OPEN");

        return ResponseEntity.ok(taskRepo.save(t));
    }

    @PostMapping("/{id}/done")
    public ResponseEntity<?> markDone(@PathVariable Long id) {
        Task t = taskRepo.findById(id).orElse(null);

        if (t == null) {
            return ResponseEntity.notFound().build();
        }

        t.setStatus("DONE");
        Task saved = taskRepo.save(t);

        List<Block> blocks = blockRepo.findByTaskIdOrderByStartTimeAsc(id);

        for (Block b : blocks) {
            b.setStatus("DONE");
        }

        blockRepo.saveAll(blocks);

        return ResponseEntity.ok(saved);
    }
}