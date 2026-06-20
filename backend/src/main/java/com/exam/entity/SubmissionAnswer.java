package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "submission_answers", indexes = {
    @Index(name = "idx_answer_submission", columnList = "submission_id"),
    @Index(name = "idx_answer_question", columnList = "question_id")
})
public class SubmissionAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
}
