package ie.ncirl.timeblocker.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.ResponseEntity;

import ie.ncirl.timeblocker.service.PlanService;

public class ReplanControllerTest {

    @Test
    void replan_returns_ok_payload() {
        PlanService planService = mock(PlanService.class);

        when(planService.replanWeek(LocalDate.parse("2025-12-08")))
                .thenReturn(new PlanService.GenerateResult(21L, LocalDate.parse("2025-12-08"), 2, 0));

        when(planService.getBlocks(21L)).thenReturn(List.of());

        ReplanController controller = new ReplanController(planService);

        ResponseEntity<?> resp = controller.replan("2025-12-08");

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();

        assertEquals(21L, ((Number) body.get("planId")).longValue());
        assertEquals("2025-12-08", body.get("weekStart"));
        assertEquals(2, ((Number) body.get("scheduledBlocks")).intValue());
        assertEquals(0, ((Number) body.get("unscheduledTasks")).intValue());
    }
}
