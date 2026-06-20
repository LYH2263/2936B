package com.exam.repository;

import com.exam.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findBySubject(String subject);
    List<Question> findBySubjectAndType(String subject, String type);
}
