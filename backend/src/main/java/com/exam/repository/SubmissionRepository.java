package com.exam.repository;

import com.exam.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByStudentUsername(String username);
    List<Submission> findByExamId(Long examId);
    List<Submission> findByExamIdAndStudentUsername(Long examId, String username);
    List<Submission> findTop5ByOrderByEndTimeDesc();

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT s.student.id) FROM Submission s")
    long countDistinctStudents();
}
