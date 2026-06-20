package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exam_reservations", indexes = {
    @Index(name = "idx_exam_student", columnList = "exam_id, student_id", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_queue_position", columnList = "exam_id, queue_position")
})
public class ExamReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    private ExamTimeSlot timeSlot;

    /**
     * 状态:
     * PENDING - 排队中
     * CONFIRMED - 已确认（获得入场资格）
     * ADMITTED - 已入场
     * EXPIRED - 超时未入场
     * CANCELLED - 已取消
     * COMPLETED - 已完成考试
     */
    @Column(nullable = false)
    private String status;

    private Integer queuePosition; // 队列中的位置

    private LocalDateTime confirmedAt; // 确认时间，开始计算入场倒计时

    private LocalDateTime admittedAt; // 实际入场时间

    private LocalDateTime expiredAt; // 过期时间

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
