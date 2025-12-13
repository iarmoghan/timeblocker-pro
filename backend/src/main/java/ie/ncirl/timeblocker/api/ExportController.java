package ie.ncirl.timeblocker.api;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.ncirl.timeblocker.domain.Block;
import ie.ncirl.timeblocker.repo.BlockRepository;
import ie.ncirl.timeblocker.repo.PlanRepository;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final DateTimeFormatter ICS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

    private final PlanRepository planRepo;
    private final BlockRepository blockRepo;

    public ExportController(PlanRepository planRepo, BlockRepository blockRepo) {
        this.planRepo = planRepo;
        this.blockRepo = blockRepo;
    }

    @GetMapping("/week")
    public ResponseEntity<String> exportWeek(@RequestParam("weekStart") String weekStart) {
        var planOpt = planRepo.findByUserIdAndWeekStart(1L, java.time.LocalDate.parse(weekStart));
        if (planOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("No plan exists for that week.");
        }

        Long planId = planOpt.get().getId();
        List<Block> blocks = blockRepo.findByPlanIdOrderByStartTimeAsc(planId);

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//TimeBlocker Pro//EN\r\n");

        for (Block b : blocks) {
            if ("SKIPPED".equalsIgnoreCase(b.getStatus())) continue;

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:block-").append(b.getId()).append("@timeblocker\r\n");
            sb.append("DTSTAMP:").append(ICS_FMT.format(Instant.now())).append("\r\n");
            sb.append("DTSTART:").append(ICS_FMT.format(b.getStartTime())).append("\r\n");
            sb.append("DTEND:").append(ICS_FMT.format(b.getEndTime())).append("\r\n");
            sb.append("SUMMARY:").append(escape(b.getTitle())).append("\r\n");
            sb.append("DESCRIPTION:Status ").append(escape(b.getStatus())).append("\r\n");
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/calendar")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"timeblocker-week.ics\"")
                .body(sb.toString());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,");
    }
}
