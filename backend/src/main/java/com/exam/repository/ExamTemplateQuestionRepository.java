package com.exam.repository;

import com.exam.entity.ExamTemplateQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamTemplateQuestionRepository extends JpaRepository<ExamTemplateQuestion, Long> {
    List<ExamTemplateQuestion> findByTemplateIdOrderBySequenceAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
