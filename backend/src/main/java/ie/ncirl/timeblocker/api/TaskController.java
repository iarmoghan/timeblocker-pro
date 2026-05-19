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

    // TaskRepository is used to save and load task records from the database.
    private final TaskRepository taskRepo;

    // BlockRepository is used here because marking a task done can also update its linked blocks.
    private final BlockRepository blockRepo;

    // This project currently uses one demo user.
    // Full login and multi-user support would be future work.
    private static final Long DEMO_USER_ID = 1L;

    public TaskController(TaskRepository taskRepo, BlockRepository blockRepo) {
        this.taskRepo = taskRepo;
        this.blockRepo = blockRepo;
    }

    // Request body used when the frontend creates a task.
    // A task can have a title, optional deadline, estimate in minutes, and priority.
    public record CreateTaskRequest(
            @NotBlank String title,
            Instant deadline,
            @Min(1) Integer estMinutes,
            Integer priority
    ) {}

    // Returns all tasks for the demo user.
    // The frontend uses this to display open and completed tasks.
    @GetMapping
    public List<Task> listAll() {
        return taskRepo.findByUserIdOrderByStatusAscDeadlineAsc(DEMO_USER_ID);
    }

    // Creates a new task from the data sent by the frontend.
    // New tasks are saved with status OPEN so the planner can schedule them.
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateTaskRequest req) {
        // Validate title because a task without a title would not be useful.
        if (req.title() == null || req.title().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Task title is required.");
        }

        // If no estimate is provided, use 60 minutes as a sensible default.
        int estMinutes = req.estMinutes() == null ? 60 : req.estMinutes();

        if (estMinutes < 1) {
            return ResponseEntity.badRequest().body("Estimate minutes must be at least 1.");
        }

        // Priority is kept between 0 and 5.
        // Higher priority tasks are scheduled earlier when deadlines are similar.
        int priority = req.priority() == null ? 0 : req.priority();

        if (priority < 0 || priority > 5) {
            return ResponseEntity.badRequest().body("Priority must be between 0 and 5.");
        }

        // Create the Task entity and save it to PostgreSQL through the repository.
        Task t = new Task();
        t.setUserId(DEMO_USER_ID);
        t.setTitle(req.title().trim());
        t.setDeadline(req.deadline());
        t.setEstMinutes(estMinutes);
        t.setPriority(priority);
        t.setStatus("OPEN");

        return ResponseEntity.ok(taskRepo.save(t));
    }

    // Manually marks a task as DONE.
    // This is separate from automatic completion through study blocks.
    @PostMapping("/{id}/done")
    public ResponseEntity<?> markDone(@PathVariable Long id) {
        Task t = taskRepo.findById(id).orElse(null);

        if (t == null) {
            return ResponseEntity.notFound().build();
        }

        // Update the task status.
        t.setStatus("DONE");
        Task saved = taskRepo.save(t);

        // Also mark linked blocks as DONE so the timeline and task list stay consistent.
        List<Block> blocks = blockRepo.findByTaskIdOrderByStartTimeAsc(id);

        for (Block b : blocks) {
            b.setStatus("DONE");
        }

        blockRepo.saveAll(blocks);

        return ResponseEntity.ok(saved);
    }
}