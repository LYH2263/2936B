package com.exam.repository;

import com.exam.entity.PkAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PkAnswerRepository extends JpaRepository<PkAnswer, Long> {
    List<PkAnswer> findBySessionIdOrderByQuestionIndexAsc(Long sessionId);

    List<PkAnswer> findBySessionIdAndPlayerIdOrderByQuestionIndexAsc(Long sessionId, Long playerId);

    Optional<PkAnswer> findBySessionIdAndPlayerIdAndQuestionIndex(Long sessionId, Long playerId, Integer questionIndex);

    long countBySessionIdAndPlayerIdAndIsCorrectTrue(Long sessionId, Long playerId);
}
