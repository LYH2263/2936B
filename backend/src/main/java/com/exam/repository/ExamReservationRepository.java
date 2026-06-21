package com.exam.repository;

import com.exam.entity.ExamReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamReservationRepository extends JpaRepository<ExamReservation, Long> {

    Optional<ExamReservation> findByExamIdAndStudentId(Long examId, Long studentId);

    @Query("SELECT r FROM ExamReservation r WHERE r.exam.id = :examId AND r.status = 'PENDING' " +
           "ORDER BY r.createdAt ASC")
    List<ExamReservation> findPendingQueueByExamId(@Param("examId") Long examId);

    @Query("SELECT COUNT(r) FROM ExamReservation r WHERE r.exam.id = :examId AND r.status = 'PENDING' " +
           "AND r.createdAt < :time")
    Integer countPendingBeforeTime(@Param("examId") Long examId, @Param("time") LocalDateTime time);

    @Query("SELECT r FROM ExamReservation r WHERE r.status = 'CONFIRMED' AND r.expiredAt <= :now")
    List<ExamReservation> findExpiredReservations(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(r) FROM ExamReservation r WHERE r.exam.id = :examId AND r.status IN ('CONFIRMED', 'ADMITTED')")
    Integer countActiveReservationsForExam(@Param("examId") Long examId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ExamReservation r WHERE r.id = :id")
    Optional<ExamReservation> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT r FROM ExamReservation r WHERE r.exam.id = :examId AND r.status IN ('PENDING', 'CONFIRMED', 'ADMITTED')")
    List<ExamReservation> findActiveReservationsByExamId(@Param("examId") Long examId);

    List<ExamReservation> findByStudentId(Long studentId);

    long countByExamId(Long examId);
}
