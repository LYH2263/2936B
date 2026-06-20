package com.exam.dto;

import lombok.Data;

@Data
public class BatchGradeItemDTO {
    private Long submissionAnswerId;
    private Long version;
    private Integer score;
    private String teacherComment;
}
