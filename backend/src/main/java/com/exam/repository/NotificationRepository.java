package com.exam.repository;

import com.exam.entity.Notification;
import com.exam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserAndIsReadOrderByCreatedAtDesc(User user, Boolean isRead);
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
}
