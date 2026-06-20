package com.exam.repository;

import com.exam.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    java.util.List<com.exam.entity.Exam> findByState(String state);
    
    @org.springframework.data.jpa.repository.Query(value = "SELECT DISTINCT e.* FROM exams e " +
            "LEFT JOIN users u ON u.username = :username " +
            "LEFT JOIN clazz_students cs ON cs.student_id = u.id " +
            "WHERE e.state = :state AND (" +
            "  e.target_audience IS NULL " +
            "  OR e.target_audience = 'ALL' " +
            "  OR (e.target_audience = 'CUSTOM' AND FIND_IN_SET(:username, e.target_ids) > 0) " +
            "  OR (e.target_audience = 'CLASS' AND cs.clazz_id IS NOT NULL AND FIND_IN_SET(CAST(cs.clazz_id AS CHAR), e.target_class_ids) > 0) " +
            ")", nativeQuery = true)
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
