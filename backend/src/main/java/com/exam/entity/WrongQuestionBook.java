package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "wrong_question_book", indexes = {
    @Index(name = "idx_wqb_student", columnList = "student_id"),
    @Index(name = "idx_wqb_question", columnList = "question_id"),
    @Index(name = "idx_wqb_student_question", columnList = "student_id, question_id", unique = true)
})
public class WrongQuestionBook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Column(columnDefinition = "TEXT")
    private String studentAnswer;

    private Integer scoreGot;

    private Integer fullScore;

    private String wrongReason;

    private Boolean mastered = false;

    private Integer wrongCount = 1;

    private LocalDateTime addedAt;

    private LocalDateTime lastWrongAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
        lastWrongAt = LocalDateTime.now();
    }
}
