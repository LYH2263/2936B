package com.exam.repository;

import com.exam.entity.ExamTimeSlot;
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
public interface ExamTimeSlotRepository extends JpaRepository<ExamTimeSlot, Long> {

    List<ExamTimeSlot> findByExamIdOrderByStartTimeAsc(Long examId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ExamTimeSlot t WHERE t.id = :id")
    Optional<ExamTimeSlot> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT t FROM ExamTimeSlot t WHERE t.exam.id = :examId " +
           "AND t.startTime <= :now AND t.endTime > :now " +
           "AND t.reservedCount < t.capacity " +
           "ORDER BY t.startTime ASC")
    List<ExamTimeSlot> findAvailableSlotsForExam(@Param("examId") Long examId, @Param("now") LocalDateTime now);

    @Query("SELECT COALESCE(SUM(t.activeCount), 0) FROM ExamTimeSlot t WHERE t.exam.id = :examId")
    Integer getTotalActiveUsersForExam(@Param("examId") Long examId);
}
