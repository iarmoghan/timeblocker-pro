package ie.ncirl.timeblocker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor
@Entity
@Table(name = "source_feeds")
public class SourceFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="feed_type", nullable=false)
    private String feedType;

    @Column(nullable=false)
    private String name;

    @Column(name="created_at", nullable=false)
    private Instant createdAt = Instant.now();
}
