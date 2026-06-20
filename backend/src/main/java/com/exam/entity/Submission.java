package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "submissions", indexes = {
    @Index(name = "idx_submission_exam", columnList = "exam_id"),
    @Index(name = "idx_submission_student", columnList = "student_id")
})
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private User student;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Integer score; // Total score

    private String state; // IN_PROGRESS, SUBMITTED

    private Integer tabSwitchCount = 0;

    @Column(columnDefinition = "TEXT")
    private String cheatingLogs;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SubmissionAnswer> answers;

    @Transient
    private Integer ranking;

    @Transient
    private Integer examTotalScore;
}
