package com.exam.repository;

import com.exam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Boolean existsByUsername(String username);
    java.util.List<User> findByRole(String role);
    java.util.List<User> findByRoleAndClazz(String role, String clazz);
    org.springframework.data.domain.Page<User> findByUsernameContainingOrFullNameContaining(String username, String fullName, org.springframework.data.domain.Pageable pageable);
    
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.role != 'ADMIN' AND (u.username LIKE %:keyword% OR u.fullName LIKE %:keyword%)")
    org.springframework.data.domain.Page<User> searchExcludingAdmins(String keyword, org.springframework.data.domain.Pageable pageable);
    
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.role != 'ADMIN'")
    org.springframework.data.domain.Page<User> findAllExcludingAdmins(org.springframework.data.domain.Pageable pageable);

    long countByRole(String role);
}
