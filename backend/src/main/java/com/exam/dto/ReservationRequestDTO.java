package com.exam.dto;

import lombok.Data;

@Data
public class ReservationRequestDTO {
    private Long examId;
    private Long timeSlotId;
}
