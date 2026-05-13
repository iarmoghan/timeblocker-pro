package ie.ncirl.timeblocker.api;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
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
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockRepository blockRepo;
    private final TaskRepository taskRepo;

    private static final Set<String> ALLOWED = Set.of("PLANNED", "DONE", "SKIPPED");

    public BlockController(BlockRepository blockRepo, TaskRepository taskRepo) {
        this.blockRepo = blockRepo;
        this.taskRepo = taskRepo;
    }

    public record UpdateStatusRequest(@NotBlank String status) {}

    @PostMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        String newStatus = req.status().toUpperCase();

        if (!ALLOWED.contains(newStatus)) {
            return ResponseEntity.badRequest().body("Invalid status. Use PLANNED, DONE, or SKIPPED.");
        }

        Block block = blockRepo.findById(id).orElseThrow();
        block.setStatus(newStatus);

        Block savedBlock = blockRepo.save(block);

        syncTaskStatusByCompletedMinutes(savedBlock.getTaskId());

        return ResponseEntity.ok(savedBlock);
    }

    private void syncTaskStatusByCompletedMinutes(Long taskId) {
        if (taskId == null) {
            return;
        }

        Task task = taskRepo.findById(taskId).orElse(null);

        if (task == null) {
            return;
        }

        List<Block> taskBlocks = blockRepo.findByTaskIdOrderByStartTimeAsc(taskId);

        long doneMinutes = taskBlocks.stream()
                .filter(block -> "DONE".equalsIgnoreCase(block.getStatus()))
                .mapToLong(block -> Duration.between(block.getStartTime(), block.getEndTime()).toMinutes())
                .sum();

        int estimatedMinutes = task.getEstMinutes() == null ? 0 : task.getEstMinutes();

        if (estimatedMinutes > 0 && doneMinutes >= estimatedMinutes) {
            task.setStatus("DONE");
            taskRepo.save(task);
            return;
        }

        if ("DONE".equalsIgnoreCase(task.getStatus()) && doneMinutes < estimatedMinutes) {
            task.setStatus("OPEN");
            taskRepo.save(task);
        }
    }
}