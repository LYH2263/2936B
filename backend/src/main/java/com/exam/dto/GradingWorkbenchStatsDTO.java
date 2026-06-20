package com.exam.dto;

import lombok.Data;

@Data
public class GradingWorkbenchStatsDTO {
    private long todayGradedCount;
    private double avgGradingSeconds;
    private long totalPendingCount;
    private long todayTotalSeconds;
}
