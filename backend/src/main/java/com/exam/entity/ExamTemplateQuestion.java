package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "exam_template_questions", indexes = {
    @Index(name = "idx_tq_template", columnList = "template_id")
})
public class ExamTemplateQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private ExamTemplate template;

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    private Integer score;

    private Integer sequence;
}
