package ie.ncirl.timeblocker.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.domain.UserPreferences;
import ie.ncirl.timeblocker.repo.UserPreferencesRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {

    private static final Long DEMO_USER_ID = 1L;

    private final UserPreferencesRepository repo;

    public PreferencesController(UserPreferencesRepository repo) {
        this.repo = repo;
    }

    public record UpdatePrefs(
            @Min(0) @Max(23) Integer dayStartHour,
            @Min(0) @Max(23) Integer dayEndHour,
            @Min(15) @Max(240) Integer blockMinutes
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
    public UserPreferences update(@Valid @RequestBody UpdatePrefs req) {
        UserPreferences p = get();

        if (req.dayStartHour() != null) p.setDayStartHour(req.dayStartHour());
        if (req.dayEndHour() != null) p.setDayEndHour(req.dayEndHour());
        if (req.blockMinutes() != null) p.setBlockMinutes(req.blockMinutes());

        // simple validation
        if (p.getDayEndHour() <= p.getDayStartHour()) {
            // reset to safe defaults if user gives invalid range
            p.setDayStartHour(8);
            p.setDayEndHour(20);
        }

        p.setUpdatedAt(Instant.now());
        return repo.save(p);
    }
}
