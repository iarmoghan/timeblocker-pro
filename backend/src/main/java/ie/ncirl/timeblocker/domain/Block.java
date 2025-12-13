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
@Table(name = "blocks")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="plan_id", nullable=false)
    private Long planId;

    @Column(name="task_id")
    private Long taskId;

    @Column(nullable=false)
    private String title;

    @Column(name="start_time", nullable=false)
    private Instant startTime;

    @Column(name="end_time", nullable=false)
    private Instant endTime;

    @Column(nullable=false)
    private String status = "PLANNED";
}
