package ie.ncirl.timeblocker.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ie.ncirl.timeblocker.service.IcsImportService;

@RestController
@RequestMapping("/api/feeds")
public class FeedController {

    private final IcsImportService importService;

    public FeedController(IcsImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/{feedId}/import")
    public ResponseEntity<?> importIcs(@PathVariable Long feedId, @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file uploaded");
            }
            int count = importService.importIcs(feedId, file.getInputStream());
            return ResponseEntity.ok().body(java.util.Map.of("imported", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("ICS parse/import error: " + e.getMessage());
        }
    }
}
