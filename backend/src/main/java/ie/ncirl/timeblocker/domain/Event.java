package ie.ncirl.timeblocker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="source_feed_id", nullable=false)
    private Long sourceFeedId;

    @Column(nullable=false)
    private String fingerprint;

    @Column
    private String uid;

    @Column(nullable=false)
    private String title;

    @Column
    private String description;

    @Column
    private String location;

    @Column(name="start_time", nullable=false)
    private Instant startTime;

    @Column(name="end_time", nullable=false)
    private Instant endTime;

    @Column(name="all_day", nullable=false)
    private boolean allDay = false;
}
