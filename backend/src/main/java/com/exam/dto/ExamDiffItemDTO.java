package com.exam.dto;

import lombok.Data;

@Data
public class ExamDiffItemDTO {
    private String changeType;
    private ExamVersionQuestionDTO leftQuestion;
    private ExamVersionQuestionDTO rightQuestion;
    private Integer scoreChange;
    private Integer sequenceChange;
}
