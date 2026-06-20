package com.exam.repository;

import com.exam.entity.PkSession;
import com.exam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PkSessionRepository extends JpaRepository<PkSession, Long> {
    List<PkSession> findByStateOrderByCreatedAtDesc(String state);

    Optional<PkSession> findFirstByStateAndPlayer1IsNullOrderByCreatedAtAsc(String state);

    @Query("SELECT p FROM PkSession p WHERE p.state = 'WAITING' AND p.player1.id != :playerId AND p.isBotGame = false ORDER BY p.createdAt ASC")
    List<PkSession> findAvailableSessions(@Param("playerId") Long playerId);

    @Query("SELECT p FROM PkSession p WHERE (p.player1.id = :playerId OR p.player2.id = :playerId) AND p.state = 'IN_PROGRESS' ORDER BY p.createdAt DESC")
    Optional<PkSession> findActiveSessionByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT p FROM PkSession p WHERE (p.player1.id = :playerId OR p.player2.id = :playerId) AND p.state = 'FINISHED' ORDER BY p.endTime DESC")
    List<PkSession> findFinishedSessionsByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT COUNT(p) FROM PkSession p WHERE (p.player1.id = :playerId OR p.player2.id = :playerId) AND p.state = 'FINISHED' AND p.winner.id = :playerId")
    long countWinsByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT COUNT(p) FROM PkSession p WHERE (p.player1.id = :playerId OR p.player2.id = :playerId) AND p.state = 'FINISHED'")
    long countTotalGamesByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT p FROM PkSession p WHERE p.state = 'FINISHED' AND p.endTime >= :startTime AND p.isBotGame = false ORDER BY p.endTime DESC")
    List<PkSession> findWeeklyRankedSessions(@Param("startTime") LocalDateTime startTime);
}
