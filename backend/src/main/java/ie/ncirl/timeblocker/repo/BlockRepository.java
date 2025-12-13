package ie.ncirl.timeblocker.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ie.ncirl.timeblocker.domain.Block;

public interface BlockRepository extends JpaRepository<Block, Long> {
    List<Block> findByPlanIdOrderByStartTimeAsc(Long planId);
    void deleteByPlanId(Long planId);

    List<Block> findByTaskIdOrderByStartTimeAsc(Long taskId);

    // Blocks that overlap a time range for a plan
    List<Block> findByPlanIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
            Long planId, Instant rangeEnd, Instant rangeStart
    );
}
