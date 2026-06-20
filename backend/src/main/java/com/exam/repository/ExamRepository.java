package com.exam.repository;

import com.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    java.util.List<com.exam.entity.Exam> findByState(String state);
    
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM exams e WHERE e.state = :state AND (e.target_audience IS NULL OR e.target_audience = 'ALL' OR FIND_IN_SET(:username, e.target_ids) > 0)", nativeQuery = true)
    java.util.List<com.exam.entity.Exam> findVisibleExams(@org.springframework.data.repository.query.Param("state") String state, @org.springframework.data.repository.query.Param("username") String username);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT e FROM Exam e " +
            "JOIN ExamQuestion eq ON eq.exam = e " +
            "JOIN eq.question q " +
            "WHERE e.creator.id = :teacherId " +
            "AND q.type IN ('SHORT') " +
            "AND e.state IN ('PUBLISHED', 'ENDED') " +
            "ORDER BY e.endTime DESC")
    java.util.List<com.exam.entity.Exam> findExamsWithSubjectiveQuestionsByTeacher(
            @org.springframework.data.repository.query.Param("teacherId") Long teacherId);
}
