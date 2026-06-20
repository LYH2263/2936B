package com.exam.repository;

import com.exam.entity.LearningAlert;
import com.exam.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LearningAlertRepository extends JpaRepository<LearningAlert, Long> {

    Page<LearningAlert> findByIsResolvedOrderBySeverityAscCreatedAtDesc(Boolean isResolved, Pageable pageable);

    Page<LearningAlert> findAllByOrderBySeverityAscCreatedAtDesc(Pageable pageable);

    List<LearningAlert> findByStudentAndAlertTypeAndIsResolved(User student, String alertType, Boolean isResolved);

    @Query("SELECT la FROM LearningAlert la WHERE la.student.id = :studentId AND la.alertType = :alertType AND la.isResolved = false " +
           "AND la.createdAt > :since ORDER BY la.createdAt DESC")
    List<LearningAlert> findRecentUnresolvedByStudentAndType(
            @Param("studentId") Long studentId,
            @Param("alertType") String alertType,
            @Param("since") LocalDateTime since);

    @Query("SELECT la FROM LearningAlert la WHERE " +
           "(:studentId IS NULL OR la.student.id = :studentId) " +
           "AND (:alertType IS NULL OR la.alertType = :alertType) " +
           "AND (:severity IS NULL OR la.severity = :severity) " +
           "AND (:isResolved IS NULL OR la.isResolved = :isResolved) " +
           "ORDER BY CASE la.severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, la.createdAt DESC")
    Page<LearningAlert> findByFilters(
            @Param("studentId") Long studentId,
            @Param("alertType") String alertType,
            @Param("severity") String severity,
            @Param("isResolved") Boolean isResolved,
            Pageable pageable);

    @Query("SELECT COUNT(la) FROM LearningAlert la WHERE la.isResolved = false")
    long countUnresolved();

    @Query("SELECT la.severity, COUNT(la) FROM LearningAlert la WHERE la.isResolved = false GROUP BY la.severity")
    List<Object[]> countBySeverityUnresolved();

    @Query("SELECT la.alertType, COUNT(la) FROM LearningAlert la WHERE la.isResolved = false GROUP BY la.alertType")
    List<Object[]> countByTypeUnresolved();

    @Modifying
    @Transactional
    @Query("UPDATE LearningAlert la SET la.isResolved = true, la.resolvedAt = :now, la.resolvedBy = :user WHERE la.id = :id")
    void resolveAlert(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("user") User user);

    @Query("SELECT la FROM LearningAlert la WHERE la.student.id = :studentId AND la.isResolved = false ORDER BY la.createdAt DESC")
    List<LearningAlert> findUnresolvedByStudent(@Param("studentId") Long studentId);

    Optional<LearningAlert> findTopByStudentAndAlertTypeAndRelatedDataContainingOrderByCreatedAtDesc(
            User student, String alertType, String relatedDataFragment);
}
