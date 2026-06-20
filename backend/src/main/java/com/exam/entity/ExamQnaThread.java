package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "exam_qna_threads", indexes = {
    @Index(name = "idx_qna_exam", columnList = "exam_id"),
    @Index(name = "idx_qna_student", columnList = "student_id"),
    @Index(name = "idx_qna_exam_student", columnList = "exam_id, student_id"),
    @Index(name = "idx_qna_is_faq", columnList = "isFaq"),
    @Index(name = "idx_qna_is_pinned", columnList = "isPinned"),
    @Index(name = "idx_qna_created", columnList = "createdAt")
})
public class ExamQnaThread {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String questionContent;

    @Column(nullable = false)
    private Boolean isFaq = false;

    @Column(nullable = false)
    private Boolean isPinned = false;

    @Column(nullable = false)
    private Boolean isAnswered = false;

    private LocalDateTime answeredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by")
    private User answeredBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ExamQnaMessage> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
