package com.exam.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExamVersionQuestionDTO {
    private Long questionId;
    private Integer score;
    private Integer sequence;
    private String content;
    private String type;
    private String subject;
}
