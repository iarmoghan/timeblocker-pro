package ie.ncirl.timeblocker.api;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.domain.Block;
import ie.ncirl.timeblocker.repo.BlockRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockRepository blockRepo;
    private static final Set<String> ALLOWED = Set.of("PLANNED", "DONE", "SKIPPED");

    public BlockController(BlockRepository blockRepo) {
        this.blockRepo = blockRepo;
    }

    public record UpdateStatusRequest(@NotBlank String status) {}

    @PostMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        String s = req.status().toUpperCase();
        if (!ALLOWED.contains(s)) {
            return ResponseEntity.badRequest().body("Invalid status. Use PLANNED, DONE, or SKIPPED.");
        }

        Block b = blockRepo.findById(id).orElseThrow();
        b.setStatus(s);
        blockRepo.save(b);
        return ResponseEntity.ok(b);
    }
}
