package com.exam.repository;

import com.exam.entity.ExamVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamVersionRepository extends JpaRepository<ExamVersion, Long> {

    List<ExamVersion> findByExamIdOrderByVersionNumberDesc(Long examId);

    List<ExamVersion> findByExamIdOrderByCreatedAtAsc(Long examId);

    Optional<ExamVersion> findByExamIdAndVersionNumber(Long examId, Integer versionNumber);

    @Query("SELECT MAX(ev.versionNumber) FROM ExamVersion ev WHERE ev.exam.id = :examId")
    Integer findMaxVersionNumberByExamId(@Param("examId") Long examId);

    long countByExamId(Long examId);
}
