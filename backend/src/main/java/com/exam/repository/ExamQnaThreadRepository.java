package com.exam.repository;

import com.exam.entity.Exam;
import com.exam.entity.ExamQnaThread;
import com.exam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamQnaThreadRepository extends JpaRepository<ExamQnaThread, Long> {

    List<ExamQnaThread> findByExamOrderByIsPinnedDescCreatedAtDesc(Exam exam);

    List<ExamQnaThread> findByExamAndStudentOrderByCreatedAtDesc(Exam exam, User student);

    List<ExamQnaThread> findByExamAndIsFaqTrueOrderByIsPinnedDescCreatedAtDesc(Exam exam);

    Optional<ExamQnaThread> findByIdAndExamId(Long id, Long examId);

    @Query("SELECT t FROM ExamQnaThread t WHERE t.exam.id = :examId AND " +
           "(t.student.id = :studentId OR t.isFaq = true) " +
           "ORDER BY t.isPinned DESC, t.createdAt DESC")
    List<ExamQnaThread> findVisibleForStudent(
            @Param("examId") Long examId,
            @Param("studentId") Long studentId);

    @Query("SELECT t FROM ExamQnaThread t WHERE t.exam.id = :examId " +
           "ORDER BY t.isPinned DESC, t.isAnswered ASC, t.createdAt DESC")
    List<ExamQnaThread> findAllForTeacher(@Param("examId") Long examId);

    @Query("SELECT COUNT(t) FROM ExamQnaThread t WHERE t.isAnswered = false")
    long countUnanswered();

    @Query("SELECT COUNT(t) FROM ExamQnaThread t WHERE t.exam.creator.id = :teacherId AND t.isAnswered = false")
    long countUnansweredByTeacher(@Param("teacherId") Long teacherId);

    @Query("SELECT t FROM ExamQnaThread t WHERE t.exam.creator.id = :teacherId AND t.isAnswered = false " +
           "ORDER BY t.createdAt DESC")
    List<ExamQnaThread> findUnansweredByTeacher(@Param("teacherId") Long teacherId);
}
