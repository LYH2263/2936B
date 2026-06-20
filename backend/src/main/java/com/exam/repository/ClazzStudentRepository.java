package com.exam.repository;

import com.exam.entity.ClazzStudent;
import com.exam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClazzStudentRepository extends JpaRepository<ClazzStudent, Long> {
    List<ClazzStudent> findByClazzId(Long clazzId);
    List<ClazzStudent> findByStudentId(Long studentId);
    Optional<ClazzStudent> findByClazzIdAndStudentId(Long clazzId, Long studentId);
    void deleteByClazzIdAndStudentId(Long clazzId, Long studentId);
    void deleteByClazzId(Long clazzId);
    
    @org.springframework.data.jpa.repository.Query("SELECT cs.student FROM ClazzStudent cs WHERE cs.clazz.id = :clazzId")
    List<User> findStudentsByClazzId(Long clazzId);
    
    @org.springframework.data.jpa.repository.Query("SELECT cs.clazz FROM ClazzStudent cs WHERE cs.student.id = :studentId")
    List<com.exam.entity.Clazz> findClazzesByStudentId(Long studentId);
    
    long countByClazzId(Long clazzId);
    boolean existsByClazzIdAndStudentId(Long clazzId, Long studentId);
}
