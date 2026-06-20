package com.exam.repository;

import com.exam.entity.AnswerSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerSnapshotRepository extends JpaRepository<AnswerSnapshot, Long> {

    List<AnswerSnapshot> findBySubmissionIdOrderByTimestampAsc(Long submissionId);

    @Query("SELECT s FROM AnswerSnapshot s WHERE s.submission.id = :submissionId ORDER BY s.timestamp DESC LIMIT 1")
    Optional<AnswerSnapshot> findLatestBySubmissionId(@Param("submissionId") Long submissionId);

    @Query("SELECT COUNT(s) FROM AnswerSnapshot s WHERE s.submission.id = :submissionId")
    long countBySubmissionId(@Param("submissionId") Long submissionId);

    void deleteBySubmissionId(Long submissionId);
}
