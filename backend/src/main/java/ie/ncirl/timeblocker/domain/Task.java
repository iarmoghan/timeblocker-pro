package ie.ncirl.timeblocker.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(nullable=false)
    private String title;

    @Column
    private Instant deadline;

    @Column(name="est_minutes", nullable=false)
    private Integer estMinutes = 60;

    @Column(nullable=false)
    private Integer priority = 0;

    @Column(nullable=false)
    private String status = "OPEN";

    @Column(name="created_at", nullable=false)
    private Instant createdAt = Instant.now();
}
