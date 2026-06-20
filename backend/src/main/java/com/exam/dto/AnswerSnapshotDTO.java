package com.exam.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AnswerSnapshotDTO {

    private Long submissionId;

    private Integer currentQuestionIndex;

    private Map<Long, String> answers;

    private Integer timeLeft;

    private Integer elapsedSeconds;
}
