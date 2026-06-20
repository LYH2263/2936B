package com.exam.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ExamStatistics {
    private Long examId;
    private String title;
    private int totalParticipants;
    private int totalSubmissions;
    private int absentCount;
    private double averageScore;
    private int maxScore;
    private int minScore;
    private double passRate; // percentage
    private Map<String, Integer> scoreDistribution; // e.g. "0-59": 5
    
    private List<StudentRanking> rankings;
    private List<QuestionStat> questionAnalysis;

    @Data
    public static class StudentRanking {
        private String username;
        private String fullName;
        private Integer score;
        private Integer rank;
        private Long submissionId;
    }

    @Data
    public static class QuestionStat {
        private Long questionId;
        private String content;
        private String type;
        private Integer sequence;
        private double averageScore;
        private double correctRate;
        private Map<String, Integer> optionDistribution; // For SINGLE/MULTI/JUDGE: "A": 10, "B": 2
    }
}
