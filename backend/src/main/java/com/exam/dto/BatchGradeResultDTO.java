package com.exam.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchGradeResultDTO {
    private int successCount;
    private int failedCount;
    private List<String> failedMessages;
    private int remainingCount;
}
