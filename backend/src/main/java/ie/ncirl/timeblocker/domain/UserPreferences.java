package ie.ncirl.timeblocker.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name="user_id")
    private Long userId;

    @Column(name="day_start_hour", nullable=false)
    private Integer dayStartHour = 8;

    @Column(name="day_end_hour", nullable=false)
    private Integer dayEndHour = 20;

    @Column(name="block_minutes", nullable=false)
    private Integer blockMinutes = 60;

    @Column(name="updated_at", nullable=false)
    private Instant updatedAt = Instant.now();
}
