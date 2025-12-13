package ie.ncirl.timeblocker.api;

import ie.ncirl.timeblocker.domain.Event;
import ie.ncirl.timeblocker.repo.EventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository repo;

    public EventController(EventRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Event> listAllForDemo() {
        // midpoint-fast: return everything ordered by start time
        return repo.findAllByOrderByStartTimeAsc();
    }
}
