package com.exam.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GradingQueueItemDTO {
    private Long submissionAnswerId;
    private Long submissionId;
    private Long studentId;
    private String studentName;
    private Long questionId;
    private String questionContent;
    private Integer questionScore;
    private String studentAnswer;
    private Integer currentScore;
    private LocalDateTime submitTime;
    private Long version;

    public GradingQueueItemDTO() {}

    public GradingQueueItemDTO(Long submissionAnswerId, Long submissionId, Long studentId,
                               String studentName, Long questionId, String questionContent,
                               Integer questionScore, String studentAnswer, Integer currentScore,
                               LocalDateTime submitTime, Long version) {
        this.submissionAnswerId = submissionAnswerId;
        this.submissionId = submissionId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.questionId = questionId;
        this.questionContent = questionContent;
        this.questionScore = questionScore;
        this.studentAnswer = studentAnswer;
        this.currentScore = currentScore;
        this.submitTime = submitTime;
        this.version = version;
    }
}
