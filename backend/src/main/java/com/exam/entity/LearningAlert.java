package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "learning_alerts", indexes = {
    @Index(name = "idx_alert_student", columnList = "student_id"),
    @Index(name = "idx_alert_type", columnList = "alertType"),
    @Index(name = "idx_alert_severity", columnList = "severity"),
    @Index(name = "idx_alert_resolved", columnList = "isResolved"),
    @Index(name = "idx_alert_created", columnList = "createdAt"),
    @Index(name = "idx_alert_student_type_resolved", columnList = "student_id,alertType,isResolved")
})
public class LearningAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(nullable = false)
    private String alertType; // CONSECUTIVE_LOW_SCORE, KNOWLEDGE_POINT_LOW, LONG_TIME_NO_EXAM

    @Column(nullable = false)
    private String severity; // HIGH, MEDIUM, LOW

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(columnDefinition = "TEXT")
    private String relatedData; // JSON: store extra data like knowledgePoint name, exam IDs, scores trend, etc.

    @Column(nullable = false)
    private Boolean isResolved = false;

    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
