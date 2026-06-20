package com.exam.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReplayDataDTO {

    private Long submissionId;

    private Long examId;

    private String examTitle;

    private String studentName;

    private Integer totalDuration;

    private List<ExamQuestionDTO> questions;

    private List<ReplaySnapshotDTO> timeline;

    private Boolean canViewAnalysis;

    private Boolean isTeacherView;

    @Data
    public static class ExamQuestionDTO {
        private Long id;
        private Long questionId;
        private String type;
        private String content;
        private String options;
        private Integer score;
        private Integer sequence;
    }

    @Data
    public static class ReplaySnapshotDTO {
        private Long id;
        private Integer elapsedSeconds;
        private Integer currentQuestionIndex;
        private Map<Long, String> answers;
        private Integer timeLeft;
    }
}
