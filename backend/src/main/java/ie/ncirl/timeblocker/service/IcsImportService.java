package ie.ncirl.timeblocker.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.ncirl.timeblocker.domain.Event;
import ie.ncirl.timeblocker.repo.EventRepository;
import ie.ncirl.timeblocker.repo.SourceFeedRepository;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;

@Service
public class IcsImportService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Dublin");

    private final SourceFeedRepository feedRepo;
    private final EventRepository eventRepo;

    public IcsImportService(SourceFeedRepository feedRepo, EventRepository eventRepo) {
        this.feedRepo = feedRepo;
        this.eventRepo = eventRepo;
    }

    @Transactional
    public int importIcs(Long feedId, InputStream inputStream) throws Exception {
        feedRepo.findById(feedId).orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));

        // Read + sanitize real-world .ics files (tabs/BOM + LAST-MODIFIED without Z breaks parsing)
        byte[] raw = inputStream.readAllBytes();
        String cleaned = new String(raw, StandardCharsets.UTF_8)
                .replace("\uFEFF", "")   // remove BOM if present
                .replace("\t", " ")      // replace TABs with spaces
                // Fix LAST-MODIFIED values like 20250910T141454 (missing Z). Make them UTC.
                .replaceAll("(?m)^LAST-MODIFIED:(\\d{8}T\\d{6})$", "LAST-MODIFIED:$1Z");

        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(new ByteArrayInputStream(cleaned.getBytes(StandardCharsets.UTF_8)));

        int imported = 0;

        for (Object obj : calendar.getComponents(Component.VEVENT)) {
            VEvent ve = (VEvent) obj;

            Temporal startTemporal = ve.getStartDate()
                    .orElseThrow(() -> new IllegalArgumentException("VEVENT missing DTSTART"))
                    .getDate();

            Temporal endTemporal = ve.getEndDate()
                    .map(DtEnd::getDate)
                    .orElse(null);

            boolean allDay = (startTemporal instanceof LocalDate);

            Instant start = temporalToInstant(startTemporal);
            Instant end;

            if (endTemporal != null) {
                end = temporalToInstant(endTemporal);
            } else {
                end = allDay ? start.plus(Duration.ofDays(1)) : start.plus(Duration.ofHours(1));
            }

            String uid = getText(ve, Property.UID).orElse(null);
            String title = getText(ve, Property.SUMMARY).orElse("(No title)");
            String desc = getText(ve, Property.DESCRIPTION).orElse(null);
            String loc  = getText(ve, Property.LOCATION).orElse(null);

            String fingerprint = sha256(feedId + "|" + safe(uid) + "|" + safe(title) + "|" + start + "|" + end);

            if (eventRepo.existsBySourceFeedIdAndFingerprint(feedId, fingerprint)) {
                continue;
            }

            Event e = new Event();
            e.setSourceFeedId(feedId);
            e.setFingerprint(fingerprint);
            e.setUid(uid);
            e.setTitle(title);
            e.setDescription(desc);
            e.setLocation(loc);
            e.setStartTime(start);
            e.setEndTime(end);
            e.setAllDay(allDay);

            eventRepo.save(e);
            imported++;
        }

        return imported;
    }

    private static Instant temporalToInstant(Temporal t) {
        if (t instanceof Instant i) return i;
        if (t instanceof ZonedDateTime zdt) return zdt.toInstant();
        if (t instanceof OffsetDateTime odt) return odt.toInstant();

        if (t instanceof LocalDateTime ldt) {
            return ldt.atZone(DEFAULT_ZONE).toInstant();
        }

        if (t instanceof LocalDate ld) {
            return ld.atStartOfDay(DEFAULT_ZONE).toInstant();
        }

        return Instant.now();
    }

    private static Optional<String> getText(VEvent ve, String propName) {
        return ve.getProperty(propName).map(Property::getValue);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
