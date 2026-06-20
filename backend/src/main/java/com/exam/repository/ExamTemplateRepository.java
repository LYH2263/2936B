package com.exam.repository;

import com.exam.entity.ExamTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ExamTemplateRepository extends JpaRepository<ExamTemplate, Long> {

    List<ExamTemplate> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);

    @Query("SELECT t FROM ExamTemplate t WHERE " +
           "(t.visibility = 'PUBLIC' AND t.reviewStatus = 'APPROVED') " +
           "OR t.creator.id = :creatorId " +
           "ORDER BY t.createdAt DESC")
    List<ExamTemplate> findVisibleTemplates(@Param("creatorId") Long creatorId);

    @Query("SELECT t FROM ExamTemplate t WHERE " +
           "((t.visibility = 'PUBLIC' AND t.reviewStatus = 'APPROVED') " +
           "OR t.creator.id = :creatorId) " +
           "AND (:course IS NULL OR t.course = :course) " +
           "AND (:keyword IS NULL OR t.name LIKE %:keyword% OR t.tags LIKE %:keyword%) " +
           "ORDER BY t.createdAt DESC")
    List<ExamTemplate> searchTemplates(@Param("creatorId") Long creatorId,
                                        @Param("course") String course,
                                        @Param("keyword") String keyword);

    List<ExamTemplate> findByReviewStatus(String reviewStatus);
}
