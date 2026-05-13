package ie.ncirl.timeblocker.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ie.ncirl.timeblocker.domain.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserIdAndStatusOrderByDeadlineAsc(Long userId, String status);

    List<Task> findByUserIdOrderByStatusAscDeadlineAsc(Long userId);
}