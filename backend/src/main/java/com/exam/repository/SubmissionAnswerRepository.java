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

    @Query("SELECT new com.exam.dto.GradingQueueItemDTO(sa.id, s.id, st.id, st.fullName, " +
            "q.id, q.content, eq.score, sa.studentAnswer, sa.score, s.endTime, sa.version) " +
            "FROM SubmissionAnswer sa " +
            "JOIN sa.submission s " +
            "JOIN s.student st " +
            "JOIN sa.question q " +
            "JOIN ExamQuestion eq ON eq.exam = s.exam AND eq.question = q " +
            "WHERE s.exam.id = :examId " +
            "AND q.id = :questionId " +
            "AND s.state = 'SUBMITTED' " +
            "AND (sa.score IS NULL OR sa.grader IS NULL) " +
            "ORDER BY s.endTime ASC")
    List<com.exam.dto.GradingQueueItemDTO> findUngradedByExamAndQuestion(
            @Param("examId") Long examId,
            @Param("questionId") Long questionId);

    @Query("SELECT new com.exam.dto.GradingQueueItemDTO(sa.id, s.id, st.id, st.fullName, " +
            "q.id, q.content, eq.score, sa.studentAnswer, sa.score, s.endTime, sa.version) " +
            "FROM SubmissionAnswer sa " +
            "JOIN sa.submission s " +
            "JOIN s.student st " +
            "JOIN sa.question q " +
            "JOIN ExamQuestion eq ON eq.exam = s.exam AND eq.question = q " +
            "WHERE s.exam.id = :examId " +
            "AND q.type IN ('SHORT') " +
            "AND s.state = 'SUBMITTED' " +
            "AND (sa.score IS NULL OR sa.grader IS NULL) " +
            "ORDER BY q.id, s.endTime ASC")
    List<com.exam.dto.GradingQueueItemDTO> findAllUngradedSubjectiveByExam(
            @Param("examId") Long examId);

    @Query("SELECT COUNT(sa.id) FROM SubmissionAnswer sa " +
            "JOIN sa.submission s " +
            "JOIN sa.question q " +
            "WHERE s.exam.id = :examId " +
            "AND q.id = :questionId " +
            "AND s.state = 'SUBMITTED' " +
            "AND (sa.score IS NULL OR sa.grader IS NULL)")
    long countUngradedByExamAndQuestion(
            @Param("examId") Long examId,
            @Param("questionId") Long questionId);

    @Query("SELECT COUNT(sa.id) FROM SubmissionAnswer sa " +
            "JOIN sa.submission s " +
            "JOIN sa.question q " +
            "WHERE s.exam.creator.id = :teacherId " +
            "AND q.type IN ('SHORT') " +
            "AND s.state = 'SUBMITTED' " +
            "AND (sa.score IS NULL OR sa.grader IS NULL)")
    long countTotalUngradedForTeacher(@Param("teacherId") Long teacherId);

    @Query("SELECT COUNT(sa.id) FROM SubmissionAnswer sa " +
            "WHERE sa.grader.id = :teacherId " +
            "AND sa.gradedAt >= :startOfDay " +
            "AND sa.gradedAt < :endOfDay")
    long countTodayGradedByTeacher(
            @Param("teacherId") Long teacherId,
            @Param("startOfDay") java.time.LocalDateTime startOfDay,
            @Param("endOfDay") java.time.LocalDateTime endOfDay);

    @Query("SELECT COALESCE(SUM(sa.gradingTimeSpent), 0) FROM SubmissionAnswer sa " +
            "WHERE sa.grader.id = :teacherId " +
            "AND sa.gradedAt >= :startOfDay " +
            "AND sa.gradedAt < :endOfDay")
    Long sumTodayGradingTimeByTeacher(
            @Param("teacherId") Long teacherId,
            @Param("startOfDay") java.time.LocalDateTime startOfDay,
            @Param("endOfDay") java.time.LocalDateTime endOfDay);

    @Query("SELECT DISTINCT q.id, q.content, eq.score " +
            "FROM SubmissionAnswer sa " +
            "JOIN sa.submission s " +
            "JOIN sa.question q " +
            "JOIN ExamQuestion eq ON eq.exam = s.exam AND eq.question = q " +
            "WHERE s.exam.id = :examId " +
            "AND q.type IN ('SHORT') " +
            "AND s.state = 'SUBMITTED' " +
            "ORDER BY eq.sequence ASC")
    List<Object[]> findSubjectiveQuestionsByExam(@Param("examId") Long examId);
}
