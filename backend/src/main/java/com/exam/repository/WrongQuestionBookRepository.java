package com.exam.repository;

import com.exam.entity.WrongQuestionBook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WrongQuestionBookRepository extends JpaRepository<WrongQuestionBook, Long> {

    Optional<WrongQuestionBook> findByStudentIdAndQuestionId(Long studentId, Long questionId);

    Page<WrongQuestionBook> findByStudentIdAndMasteredFalse(Long studentId, Pageable pageable);

    Page<WrongQuestionBook> findByStudentId(Long studentId, Pageable pageable);

    @Query("SELECT w FROM WrongQuestionBook w JOIN w.question q WHERE w.student.id = :studentId " +
           "AND (:subject IS NULL OR q.subject = :subject) " +
           "AND (:knowledgePoint IS NULL OR q.knowledgePoint = :knowledgePoint) " +
           "AND (:difficulty IS NULL OR q.difficulty = :difficulty) " +
           "AND (:mastered IS NULL OR w.mastered = :mastered)")
    Page<WrongQuestionBook> findByStudentIdWithFilters(
            @Param("studentId") Long studentId,
            @Param("subject") String subject,
            @Param("knowledgePoint") String knowledgePoint,
            @Param("difficulty") Integer difficulty,
            @Param("mastered") Boolean mastered,
            Pageable pageable);

    @Query("SELECT w FROM WrongQuestionBook w JOIN FETCH w.question q WHERE w.student.id = :studentId AND w.mastered = false " +
           "ORDER BY w.wrongCount DESC, w.lastWrongAt DESC")
    List<WrongQuestionBook> findRandomWrongQuestions(@Param("studentId") Long studentId, Pageable pageable);

    long countByStudentIdAndMasteredFalse(Long studentId);

    long countByStudentIdAndMasteredTrue(Long studentId);

    @Query("SELECT COUNT(w) FROM WrongQuestionBook w WHERE w.student.id = :studentId AND w.addedAt >= :startTime")
    long countAddedSince(@Param("studentId") Long studentId, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(w) FROM WrongQuestionBook w WHERE w.student.id = :studentId AND w.mastered = true AND w.lastWrongAt >= :startTime")
    long countMasteredSince(@Param("studentId") Long studentId, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT DISTINCT q.subject FROM WrongQuestionBook w JOIN w.question q WHERE w.student.id = :studentId AND q.subject IS NOT NULL")
    List<String> findDistinctSubjectsByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT DISTINCT q.knowledgePoint FROM WrongQuestionBook w JOIN w.question q WHERE w.student.id = :studentId AND q.knowledgePoint IS NOT NULL")
    List<String> findDistinctKnowledgePointsByStudentId(@Param("studentId") Long studentId);

    @Modifying
    @Transactional
    void deleteByStudentIdAndQuestionId(Long studentId, Long questionId);
}
