package com.exam.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TimeSlotDTO {
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer reservedCount;
    private Boolean available;
}
