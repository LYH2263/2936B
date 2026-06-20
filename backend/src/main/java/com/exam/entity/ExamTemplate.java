package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_templates", indexes = {
    @Index(name = "idx_tpl_creator", columnList = "creator_id"),
    @Index(name = "idx_tpl_visibility", columnList = "visibility")
})
public class ExamTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String course;

    @Column(nullable = false)
    private String visibility = "PRIVATE";

    @Column(nullable = false)
    private String reviewStatus = "APPROVED";

    private String tags;

    private Integer duration;

    private String coverUrl;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private User creator;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
