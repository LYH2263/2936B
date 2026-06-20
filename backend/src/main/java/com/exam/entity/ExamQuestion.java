package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "exam_questions", indexes = {
    @Index(name = "idx_eq_exam", columnList = "exam_id")
})
public class ExamQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    private Integer score; // Score for this question in this exam

    private Integer sequence; // Order sequence
}
