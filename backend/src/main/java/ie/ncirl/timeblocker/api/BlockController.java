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

    // This repository is used to find and update study blocks in the database.
    private final BlockRepository blockRepo;

    // This repository is used because block progress can affect the parent task status.
    private final TaskRepository taskRepo;

    // Only these block statuses are allowed in the system.
    // PLANNED = scheduled but not completed yet
    // DONE = the student completed the study block
    // SKIPPED = the student skipped the study block
    private static final Set<String> ALLOWED = Set.of("PLANNED", "DONE", "SKIPPED");

    public BlockController(BlockRepository blockRepo, TaskRepository taskRepo) {
        this.blockRepo = blockRepo;
        this.taskRepo = taskRepo;
    }

    // Request body used when the frontend updates a block status.
    // Example JSON:
    // { "status": "DONE" }
    public record UpdateStatusRequest(@NotBlank String status) {}

    // This endpoint is called when the user clicks Done, Skip, or Reset on a study block.
    // It updates the block status and then checks if the related task should also be updated.
    @PostMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        String newStatus = req.status().toUpperCase();

        // Basic validation so only supported statuses can be saved.
        if (!ALLOWED.contains(newStatus)) {
            return ResponseEntity.badRequest().body("Invalid status. Use PLANNED, DONE, or SKIPPED.");
        }

        // Find the block by id.
        // If the id does not exist, this will currently throw an error.
        // A future improvement would be to return a cleaner 404 response.
        Block block = blockRepo.findById(id).orElseThrow();

        // Update and save the new block status.
        block.setStatus(newStatus);
        Block savedBlock = blockRepo.save(block);

        // After changing a block, check whether the parent task should become DONE or OPEN.
        syncTaskStatusByCompletedMinutes(savedBlock.getTaskId());

        return ResponseEntity.ok(savedBlock);
    }

    // This method keeps task status and block progress consistent.
    // If enough linked block time is DONE, the task becomes DONE.
    // If completed time drops below the estimate, the task becomes OPEN again.
    private void syncTaskStatusByCompletedMinutes(Long taskId) {
        if (taskId == null) {
            return;
        }

        Task task = taskRepo.findById(taskId).orElse(null);

        if (task == null) {
            return;
        }

        // Load all blocks linked to this task.
        List<Block> taskBlocks = blockRepo.findByTaskIdOrderByStartTimeAsc(taskId);

        // Add up the total minutes from blocks marked DONE.
        long doneMinutes = taskBlocks.stream()
                .filter(block -> "DONE".equalsIgnoreCase(block.getStatus()))
                .mapToLong(block -> Duration.between(block.getStartTime(), block.getEndTime()).toMinutes())
                .sum();

        int estimatedMinutes = task.getEstMinutes() == null ? 0 : task.getEstMinutes();

        // If the student has completed enough scheduled block time,
        // mark the parent task as DONE.
        if (estimatedMinutes > 0 && doneMinutes >= estimatedMinutes) {
            task.setStatus("DONE");
            taskRepo.save(task);
            return;
        }

        // If a task was previously DONE but a block is reset or changed,
        // reopen the task if the completed minutes are now below the estimate.
        if ("DONE".equalsIgnoreCase(task.getStatus()) && doneMinutes < estimatedMinutes) {
            task.setStatus("OPEN");
            taskRepo.save(task);
        }
    }
}