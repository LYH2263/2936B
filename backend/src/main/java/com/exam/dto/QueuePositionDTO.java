package com.exam.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QueuePositionDTO {
    private Long reservationId;
    private Long examId;
    private String examTitle;
    private String status;
    private Integer position;
    private Integer totalWaiting;
    private Integer estimatedWaitMinutes;
    private Integer admissionTimeout;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiredAt;
    private Long timeSlotId;
    private LocalDateTime timeSlotStart;
    private LocalDateTime timeSlotEnd;
    private Long secondsUntilExpiry;
}
