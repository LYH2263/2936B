package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "pk_sessions", indexes = {
    @Index(name = "idx_pk_state", columnList = "state"),
    @Index(name = "idx_pk_player1", columnList = "player1_id"),
    @Index(name = "idx_pk_player2", columnList = "player2_id"),
    @Index(name = "idx_pk_created", columnList = "createdAt")
})
public class PkSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "player1_id")
    private User player1;

    @ManyToOne
    @JoinColumn(name = "player2_id")
    private User player2;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private Integer questionCount = 10;

    private Integer currentQuestionIndex = 0;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer player1Score = 0;

    private Integer player2Score = 0;

    @ManyToOne
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(nullable = false)
    private Boolean isBotGame = false;

    @Column(columnDefinition = "TEXT")
    private String questionIds;

    private LocalDateTime currentQuestionStartTime;

    private LocalDateTime player1LastActive;

    private LocalDateTime player2LastActive;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
