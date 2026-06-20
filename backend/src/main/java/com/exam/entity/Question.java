package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "questions")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content; // Question text

    @Column(nullable = false)
    private String type; // SINGLE, MULTI, JUDGE, SHORT

    @Column(columnDefinition = "TEXT")
    private String options; // JSON string for options (e.g. [{"label":"A","text":"Option A"}])

    @Column(columnDefinition = "TEXT")
    private String answer; // Correct answer

    @Column(columnDefinition = "TEXT")
    private String analysis; // Explanation

    private String subject;
    private String knowledgePoint;
    private Integer difficulty; // 1-5

    private Integer defaultScore;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private User creator;
}
