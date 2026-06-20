package com.exam.dto;

import com.exam.entity.Submission;
import java.util.List;

public class StudentStatsDTO {
    private long totalExams;
    private double avgScore;
    private double passRate;
    private long pendingExamsCount;
    private List<Submission> recentSubmissions;

    // Getters and Setters
    public long getTotalExams() { return totalExams; }
    public void setTotalExams(long totalExams) { this.totalExams = totalExams; }

    public double getAvgScore() { return avgScore; }
    public void setAvgScore(double avgScore) { this.avgScore = avgScore; }

    public double getPassRate() { return passRate; }
    public void setPassRate(double passRate) { this.passRate = passRate; }

    public long getPendingExamsCount() { return pendingExamsCount; }
    public void setPendingExamsCount(long pendingExamsCount) { this.pendingExamsCount = pendingExamsCount; }

    public List<Submission> getRecentSubmissions() { return recentSubmissions; }
    public void setRecentSubmissions(List<Submission> recentSubmissions) { this.recentSubmissions = recentSubmissions; }
}
