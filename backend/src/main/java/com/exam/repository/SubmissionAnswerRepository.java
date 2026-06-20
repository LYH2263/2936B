package com.exam.repository;

import com.exam.entity.SubmissionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
