package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "submission_answers", indexes = {
    @Index(name = "idx_answer_submission", columnList = "submission_id"),
    @Index(name = "idx_answer_question", columnList = "question_id"),
    @Index(name = "idx_answer_question_score", columnList = "question_id,score")
})
public class SubmissionAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question; // Link directly to question for simplicity, or via ExamQuestion

    @Column(columnDefinition = "TEXT")
    private String studentAnswer;

    private Integer score; // Score awarded for this specific answer

    private String teacherComment;

    @ManyToOne
    @JoinColumn(name = "grader_id")
    private User grader;

    private LocalDateTime gradedAt;

    private Integer gradingTimeSpent; // in seconds
}
