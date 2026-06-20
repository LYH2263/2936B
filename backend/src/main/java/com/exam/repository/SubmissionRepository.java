package com.exam.repository;

import com.exam.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByStudentUsername(String username);
    List<Submission> findByExamId(Long examId);
    List<Submission> findByExamIdAndStudentUsername(Long examId, String username);
    List<Submission> findTop5ByOrderByEndTimeDesc();

    @Query("SELECT COUNT(DISTINCT s.student.id) FROM Submission s")
    long countDistinctStudents();

    @Query("SELECT s FROM Submission s WHERE s.student.id = :studentId AND s.state = 'SUBMITTED' AND s.endTime IS NOT NULL " +
           "ORDER BY s.endTime DESC")
    List<Submission> findSubmittedByStudentOrderByEndTimeDesc(@Param("studentId") Long studentId);

    @Query("SELECT s FROM Submission s WHERE s.student.id = :studentId AND s.state = 'SUBMITTED' AND s.endTime IS NOT NULL " +
           "ORDER BY s.endTime DESC LIMIT :limit")
    List<Submission> findLastNByStudent(@Param("studentId") Long studentId, @Param("limit") int limit);

    @Query("SELECT MAX(s.endTime) FROM Submission s WHERE s.student.id = :studentId AND s.state = 'SUBMITTED'")
    Optional<LocalDateTime> findLastExamTimeByStudent(@Param("studentId") Long studentId);

    @Query("SELECT DISTINCT s.student.id FROM Submission s WHERE s.state = 'SUBMITTED' AND s.endTime IS NOT NULL")
    List<Long> findAllStudentsWithSubmissions();

    @Query("SELECT COUNT(DISTINCT e.id) FROM Submission s " +
           "JOIN s.exam e " +
           "WHERE s.student.id = :studentId AND s.state = 'SUBMITTED' " +
           "AND e.startTime >= :startTime")
    long countExamsByStudentSince(@Param("studentId") Long studentId, @Param("startTime") LocalDateTime startTime);
}
