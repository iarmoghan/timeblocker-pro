package ie.ncirl.timeblocker.api;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.domain.UserPreferences;
import ie.ncirl.timeblocker.repo.UserPreferencesRepository;

@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {

    private static final Long DEMO_USER_ID = 1L;

    private final UserPreferencesRepository repo;

    public PreferencesController(UserPreferencesRepository repo) {
        this.repo = repo;
    }

    public record UpdatePrefs(
            Integer dayStartHour,
            Integer dayEndHour,
            Integer blockMinutes
    ) {}

    @GetMapping
    public UserPreferences get() {
        return repo.findById(DEMO_USER_ID).orElseGet(() -> {
            UserPreferences p = new UserPreferences();
            p.setUserId(DEMO_USER_ID);
            p.setUpdatedAt(Instant.now());
            return repo.save(p);
        });
    }

    @PostMapping
    public ResponseEntity<?> update(@RequestBody UpdatePrefs req) {
        UserPreferences p = get();

        int nextDayStart = req.dayStartHour() == null ? p.getDayStartHour() : req.dayStartHour();
        int nextDayEnd = req.dayEndHour() == null ? p.getDayEndHour() : req.dayEndHour();
        int nextBlockMinutes = req.blockMinutes() == null ? p.getBlockMinutes() : req.blockMinutes();

        if (nextDayStart < 0 || nextDayStart > 23) {
            return ResponseEntity.badRequest().body("Day start hour must be between 0 and 23.");
        }

        if (nextDayEnd < 0 || nextDayEnd > 23) {
            return ResponseEntity.badRequest().body("Day end hour must be between 0 and 23.");
        }

        if (nextDayStart >= nextDayEnd) {
            return ResponseEntity.badRequest().body("Day start must be earlier than day end.");
        }

        if (nextBlockMinutes < 15 || nextBlockMinutes > 240) {
            return ResponseEntity.badRequest().body("Block minutes must be between 15 and 240.");
        }

        p.setDayStartHour(nextDayStart);
        p.setDayEndHour(nextDayEnd);
        p.setBlockMinutes(nextBlockMinutes);
        p.setUpdatedAt(Instant.now());

        return ResponseEntity.ok(repo.save(p));
    }
}