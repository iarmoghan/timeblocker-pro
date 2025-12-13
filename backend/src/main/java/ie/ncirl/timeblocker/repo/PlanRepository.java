package ie.ncirl.timeblocker.repo;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ie.ncirl.timeblocker.domain.Plan;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);
    void deleteByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
