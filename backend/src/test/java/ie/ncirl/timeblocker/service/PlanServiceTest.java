package ie.ncirl.timeblocker.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ie.ncirl.timeblocker.domain.Plan;
import ie.ncirl.timeblocker.domain.Task;
import ie.ncirl.timeblocker.domain.UserPreferences;
import ie.ncirl.timeblocker.repo.BlockRepository;
import ie.ncirl.timeblocker.repo.EventRepository;
import ie.ncirl.timeblocker.repo.PlanRepository;
import ie.ncirl.timeblocker.repo.TaskRepository;
import ie.ncirl.timeblocker.repo.UserPreferencesRepository;

public class PlanServiceTest {

    @Test
    void generateWeeklyPlan_createsBlocks_forOpenTasks() {
        PlanRepository planRepo = mock(PlanRepository.class);
        BlockRepository blockRepo = mock(BlockRepository.class);
        TaskRepository taskRepo = mock(TaskRepository.class);
        EventRepository eventRepo = mock(EventRepository.class);
        UserPreferencesRepository prefsRepo = mock(UserPreferencesRepository.class);

        UserPreferences prefs = new UserPreferences();
        prefs.setUserId(1L);
        prefs.setDayStartHour(8);
        prefs.setDayEndHour(20);
        prefs.setBlockMinutes(60);
        when(prefsRepo.findById(1L)).thenReturn(Optional.of(prefs));

        Plan existing = null;
        when(planRepo.findByUserIdAndWeekStart(eq(1L), any())).thenReturn(Optional.empty());

        when(planRepo.save(any())).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setId(123L);
            return p;
        });

        when(eventRepo.findAllByOrderByStartTimeAsc()).thenReturn(List.of());

        Task t = new Task();
        t.setId(10L);
        t.setUserId(1L);
        t.setTitle("Test task");
        t.setEstMinutes(60);
        t.setPriority(1);
        t.setStatus("OPEN");
        t.setDeadline(Instant.now().plusSeconds(3600 * 24));
        when(taskRepo.findByUserIdAndStatusOrderByDeadlineAsc(1L, "OPEN")).thenReturn(List.of(t));

        PlanService svc = new PlanService(planRepo, blockRepo, taskRepo, eventRepo, prefsRepo);

        var result = svc.generateWeeklyPlan(LocalDate.parse("2025-12-08"));

        assertTrue(result.scheduledBlocks() >= 1);
        assertEquals(0, result.unscheduledTasks());

        verify(blockRepo, atLeastOnce()).save(any());
    }
}
