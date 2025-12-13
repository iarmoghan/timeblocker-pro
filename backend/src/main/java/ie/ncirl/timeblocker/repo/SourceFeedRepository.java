package ie.ncirl.timeblocker.repo;

import ie.ncirl.timeblocker.domain.SourceFeed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceFeedRepository extends JpaRepository<SourceFeed, Long> { }
