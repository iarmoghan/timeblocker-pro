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

    // Default timezone used when an .ics event does not include an exact timezone.
    // I used Europe/Dublin because this project is based around an Irish student timetable.
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Dublin");

    // SourceFeedRepository is used to check that the calendar feed exists.
    private final SourceFeedRepository feedRepo;

    // EventRepository is used to save imported timetable events into the database.
    private final EventRepository eventRepo;

    public IcsImportService(SourceFeedRepository feedRepo, EventRepository eventRepo) {
        this.feedRepo = feedRepo;
        this.eventRepo = eventRepo;
    }

    // This method imports an uploaded .ics calendar file.
    // It reads the file, parses calendar events, and saves them as Event records.
    // These imported events are later treated as fixed busy time by the planner.
    @Transactional
    public int importIcs(Long feedId, InputStream inputStream) throws Exception {
        // Make sure the source feed exists before importing events for it.
        feedRepo.findById(feedId).orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));

        // Read the uploaded .ics file into memory.
        byte[] raw = inputStream.readAllBytes();

        // Some real-world .ics files can contain small formatting issues.
        // This cleanup makes the parser more tolerant of files with BOM, tabs,
        // or LAST-MODIFIED values missing the UTC "Z".
        String cleaned = new String(raw, StandardCharsets.UTF_8)
                .replace("\uFEFF", "")   // remove BOM if present
                .replace("\t", " ")      // replace TABs with spaces
                .replaceAll("(?m)^LAST-MODIFIED:(\\d{8}T\\d{6})$", "LAST-MODIFIED:$1Z");

        // iCal4j is used here to parse the .ics calendar content.
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(new ByteArrayInputStream(cleaned.getBytes(StandardCharsets.UTF_8)));

        int imported = 0;

        // Loop through each VEVENT in the calendar.
        // VEVENT is the standard calendar component for an event.
        for (Object obj : calendar.getComponents(Component.VEVENT)) {
            VEvent ve = (VEvent) obj;

            // DTSTART is required because every event needs a start date/time.
            Temporal startTemporal = ve.getStartDate()
                    .orElseThrow(() -> new IllegalArgumentException("VEVENT missing DTSTART"))
                    .getDate();

            // DTEND may be missing in some files, so I handle that below.
            Temporal endTemporal = ve.getEndDate()
                    .map(DtEnd::getDate)
                    .orElse(null);

            // If the start value is a LocalDate, it means this is an all-day event.
            boolean allDay = (startTemporal instanceof LocalDate);

            // Convert the start time into an Instant so it can be stored consistently.
            Instant start = temporalToInstant(startTemporal);
            Instant end;

            // If the .ics event has an end time, use it.
            // Otherwise, use a default length:
            // - all-day events last 1 day
            // - timed events last 1 hour
            if (endTemporal != null) {
                end = temporalToInstant(endTemporal);
            } else {
                end = allDay ? start.plus(Duration.ofDays(1)) : start.plus(Duration.ofHours(1));
            }

            // Read useful fields from the .ics event.
            String uid = getText(ve, Property.UID).orElse(null);
            String title = getText(ve, Property.SUMMARY).orElse("(No title)");
            String desc = getText(ve, Property.DESCRIPTION).orElse(null);
            String loc  = getText(ve, Property.LOCATION).orElse(null);

            // Create a fingerprint for duplicate detection.
            // This stops the same event being imported again if the file is uploaded twice.
            String fingerprint = sha256(feedId + "|" + safe(uid) + "|" + safe(title) + "|" + start + "|" + end);

            if (eventRepo.existsBySourceFeedIdAndFingerprint(feedId, fingerprint)) {
                continue;
            }

            // Create an Event entity and save it to the database.
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

        // Return how many new events were imported.
        return imported;
    }

    // Converts different date/time formats from iCal4j into Instant.
    // Instant is useful because it stores a precise moment in time.
    private static Instant temporalToInstant(Temporal t) {
        if (t instanceof Instant i) return i;
        if (t instanceof ZonedDateTime zdt) return zdt.toInstant();
        if (t instanceof OffsetDateTime odt) return odt.toInstant();

        // If the event has a local date-time without timezone,
        // assume the default Europe/Dublin timezone.
        if (t instanceof LocalDateTime ldt) {
            return ldt.atZone(DEFAULT_ZONE).toInstant();
        }

        // If the event is all-day, start it at midnight in the default timezone.
        if (t instanceof LocalDate ld) {
            return ld.atStartOfDay(DEFAULT_ZONE).toInstant();
        }

        // Fallback. This should rarely happen, but avoids crashing on unexpected formats.
        return Instant.now();
    }

    // Helper method to safely get text properties from an .ics event.
    private static Optional<String> getText(VEvent ve, String propName) {
        return ve.getProperty(propName).map(Property::getValue);
    }

    // Avoids null values when creating the fingerprint string.
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // Creates a SHA-256 hash used as the event fingerprint.
    // This is used to detect duplicate imported events.
    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));

        return sb.toString();
    }
}