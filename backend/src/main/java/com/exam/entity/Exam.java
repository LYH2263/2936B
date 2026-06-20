package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exams")
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String course;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    private Integer duration; // in minutes

    private String state; // DRAFT, PUBLISHED, ENDED

    private String coverUrl;

    // Security Settings
    private Boolean allowTabSwitch = true;
    private Integer tabSwitchLimit = 3;
    private Boolean enableCamera = false;

    // Result Visibility
    private Boolean publicScores = true;
    private Boolean allowViewAnalysis = true;
    
    // Anti-Cheating
    private Boolean shuffleQuestions = false;
    private Boolean shuffleOptions = false;

    // Targeting
    private String targetAudience = "ALL"; // ALL, CUSTOM, CLASS
    private String targetIds; // Comma separated user IDs or usernames
    private String targetClassIds; // Comma separated class IDs for CLASS target audience

    // Reservation Mode
    private Boolean reservationEnabled = false;
    private Integer maxConcurrentUsers; // 每时段最大同时在线人数
    private Integer timeSlotDuration; // 时段时长（分钟）
    private LocalDateTime reservationStartTime; // 预约开始时间
    private LocalDateTime reservationEndTime; // 预约结束时间
    private Integer admissionTimeout = 15; // 入场超时时间（分钟）

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private User creator;
}
