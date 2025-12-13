package ie.ncirl.timeblocker.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ie.ncirl.timeblocker.domain.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsBySourceFeedIdAndFingerprint(Long sourceFeedId, String fingerprint);

    List<Event> findAllByOrderByStartTimeAsc();

    // Events that overlap a time range: start < rangeEnd AND end > rangeStart
    List<Event> findByStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(Instant rangeEnd, Instant rangeStart);
}
