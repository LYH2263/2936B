package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "answer_snapshots", indexes = {
    @Index(name = "idx_snapshot_submission", columnList = "submission_id"),
    @Index(name = "idx_snapshot_submission_time", columnList = "submission_id, timestamp")
})
public class AnswerSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private Integer elapsedSeconds;

    @Column(nullable = false)
    private Integer currentQuestionIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answersDelta;

    @Column(columnDefinition = "INTEGER", nullable = false)
    private Integer timeLeft;

    @Column(columnDefinition = "BOOLEAN", nullable = false)
    private Boolean isFullSnapshot = false;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
