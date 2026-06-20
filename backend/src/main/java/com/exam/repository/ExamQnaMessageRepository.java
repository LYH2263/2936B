package com.exam.repository;

import com.exam.entity.ExamQnaMessage;
import com.exam.entity.ExamQnaThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamQnaMessageRepository extends JpaRepository<ExamQnaMessage, Long> {

    List<ExamQnaMessage> findByThreadOrderByCreatedAtAsc(ExamQnaThread thread);

    List<ExamQnaMessage> findByThreadIdOrderByCreatedAtAsc(Long threadId);
}
