package com.exam.repository;

import com.exam.entity.SubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SubmissionAnswerRepository extends JpaRepository<SubmissionAnswer, Long> {
    java.util.List<SubmissionAnswer> findBySubmissionExamId(Long examId);

    @org.springframework.data.jpa.repository.Query("SELECT sa.question.id, AVG(sa.score), " +
            "SUM(CASE WHEN sa.score = eq.score THEN 1 ELSE 0 END) * 100.0 / COUNT(sa.id) " +
            "FROM SubmissionAnswer sa " +
            "JOIN sa.submission s " +
            "JOIN ExamQuestion eq ON eq.exam = s.exam AND eq.question = sa.question " +
            "WHERE s.exam.id = :examId " +
            "GROUP BY sa.question.id")
    java.util.List<Object[]> getQuestionAnalysis(@org.springframework.data.repository.query.Param("examId") Long examId);

    @Query("SELECT q.knowledgePoint, " +
            "COUNT(sa.id) as totalCount, " +
            "SUM(CASE WHEN sa.score > 0 AND COALESCE(eq.score, q.defaultScore, 1) > 0 " +
            "THEN CAST(sa.score AS double) / COALESCE(eq.score, q.defaultScore, 1) ELSE 0 END) / " +
            "GREATEST(1, COUNT(sa.id)) as correctRate, " +
            "SUM(CASE WHEN sa.score > 0 AND COALESCE(eq.score, q.defaultScore, 1) > 0 " +
            "AND CAST(sa.score AS double) / COALESCE(eq.score, q.defaultScore, 1) >= 0.6 THEN 1 ELSE 0 END) as passedCount " +
            "FROM SubmissionAnswer sa " +
            "JOIN sa.submission s " +
            "JOIN sa.question q " +
            "LEFT JOIN ExamQuestion eq ON eq.exam = s.exam AND eq.question = sa.question " +
            "WHERE s.student.id = :studentId " +
            "AND s.state = 'SUBMITTED' " +
            "AND s.endTime IS NOT NULL " +
            "AND s.endTime >= :since " +
            "AND q.knowledgePoint IS NOT NULL AND q.knowledgePoint != '' " +
            "GROUP BY q.knowledgePoint " +
            "HAVING COUNT(sa.id) >= :minQuestions")
    List<Object[]> getKnowledgePointStatsByStudent(
            @Param("studentId") Long studentId,
            @Param("since") LocalDateTime since,
            @Param("minQuestions") int minQuestions);

    @Query("SELECT sa FROM SubmissionAnswer sa " +
            "JOIN FETCH sa.submission s " +
            "JOIN FETCH sa.question q " +
            "WHERE s.student.id = :studentId " +
            "AND s.state = 'SUBMITTED' " +
            "AND s.endTime IS NOT NULL " +
            "AND q.knowledgePoint = :knowledgePoint " +
            "ORDER BY s.endTime DESC")
    List<SubmissionAnswer> findByStudentAndKnowledgePoint(
            @Param("studentId") Long studentId,
            @Param("knowledgePoint") String knowledgePoint);
}
