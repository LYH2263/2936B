package com.exam.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WrongQuestionBookDTO {
    private Long id;
    private Long questionId;
    private String content;
    private String type;
    private String options;
    private String answer;
    private String analysis;
    private String subject;
    private String knowledgePoint;
    private Integer difficulty;
    private Integer defaultScore;
    private String studentAnswer;
    private Integer scoreGot;
    private Integer fullScore;
    private String wrongReason;
    private Boolean mastered;
    private Integer wrongCount;
    private LocalDateTime addedAt;
    private LocalDateTime lastWrongAt;
    private Long submissionId;
}
