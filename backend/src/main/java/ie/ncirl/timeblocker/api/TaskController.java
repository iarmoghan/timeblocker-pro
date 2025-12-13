package ie.ncirl.timeblocker.api;

import java.time.Instant;
import java.util.List;

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
import jakarta.validation.Valid;
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
    public List<Task> listOpen() {
        return taskRepo.findByUserIdAndStatusOrderByDeadlineAsc(DEMO_USER_ID, "OPEN");
    }

    @PostMapping
    public Task create(@Valid @RequestBody CreateTaskRequest req) {
        Task t = new Task();
        t.setUserId(DEMO_USER_ID);
        t.setTitle(req.title());
        t.setDeadline(req.deadline());
        t.setEstMinutes(req.estMinutes() == null ? 60 : req.estMinutes());
        t.setPriority(req.priority() == null ? 0 : req.priority());
        t.setStatus("OPEN");
        return taskRepo.save(t);
    }

    @PostMapping("/{id}/done")
    public Task markDone(@PathVariable Long id) {
        Task t = taskRepo.findById(id).orElseThrow();
        t.setStatus("DONE");
        Task saved = taskRepo.save(t);

        // Also update any existing blocks for this task
        List<Block> blocks = blockRepo.findByTaskIdOrderByStartTimeAsc(id);
        for (Block b : blocks) {
            b.setStatus("DONE");
        }
        blockRepo.saveAll(blocks);

        return saved;
    }
}
