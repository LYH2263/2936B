package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "pk_answers", indexes = {
    @Index(name = "idx_pk_answer_session", columnList = "session_id"),
    @Index(name = "idx_pk_answer_player", columnList = "player_id"),
    @Index(name = "idx_pk_answer_session_question", columnList = "session_id,questionIndex")
})
public class PkAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private PkSession session;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private User player;

    @Column(nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private Integer questionIndex;

    @Column(columnDefinition = "TEXT")
    private String answer;

    private Boolean isCorrect;

    private Integer timeUsed;

    private LocalDateTime answeredAt;
}
