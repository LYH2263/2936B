package com.exam.dto;

import com.exam.entity.Submission;
import java.util.List;

public class TeacherStatsDTO {
    private long totalExams;
    private long totalSubmissions;
    private long activeExams;
    private long totalStudents;
    private List<Submission> recentSubmissions;

    public long getTotalExams() { return totalExams; }
    public void setTotalExams(long totalExams) { this.totalExams = totalExams; }

    public long getTotalSubmissions() { return totalSubmissions; }
    public void setTotalSubmissions(long totalSubmissions) { this.totalSubmissions = totalSubmissions; }

    public long getActiveExams() { return activeExams; }
    public void setActiveExams(long activeExams) { this.activeExams = activeExams; }

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public List<Submission> getRecentSubmissions() { return recentSubmissions; }
    public void setRecentSubmissions(List<Submission> recentSubmissions) { this.recentSubmissions = recentSubmissions; }
}
