package com.exam.repository;

import com.exam.entity.Clazz;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClazzRepository extends JpaRepository<Clazz, Long> {
    Optional<Clazz> findByName(String name);
    List<Clazz> findByTeacherId(Long teacherId);
    List<Clazz> findByGrade(String grade);
    boolean existsByName(String name);
}
