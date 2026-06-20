package com.exam.controller;

import com.exam.dto.QueuePositionDTO;
import com.exam.dto.ReservationRequestDTO;
import com.exam.dto.TimeSlotDTO;
import com.exam.entity.ExamReservation;
import com.exam.service.ReservationQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationQueueService reservationQueueService;

    public ReservationController(ReservationQueueService reservationQueueService) {
        this.reservationQueueService = reservationQueueService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ExamReservation> createReservation(
            @RequestBody ReservationRequestDTO request,
            Principal principal) {
        ExamReservation reservation = reservationQueueService.createReservation(
                request.getExamId(),
                request.getTimeSlotId(),
                principal.getName()
        );
        return ResponseEntity.ok(reservation);
    }

    @DeleteMapping("/{examId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable Long examId,
            Principal principal) {
        reservationQueueService.cancelReservation(examId, principal.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{examId}/position")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<QueuePositionDTO> getQueuePosition(
            @PathVariable Long examId,
            Principal principal) {
        QueuePositionDTO position = reservationQueueService.getQueuePosition(examId, principal.getName());
        return ResponseEntity.ok(position);
    }

    @GetMapping("/{examId}/timeslots")
    public ResponseEntity<List<TimeSlotDTO>> getTimeSlots(@PathVariable Long examId) {
        List<TimeSlotDTO> slots = reservationQueueService.getTimeSlots(examId);
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/{examId}/snapshot")
    public ResponseEntity<Map<String, Object>> getQueueSnapshot(@PathVariable Long examId) {
        Map<String, Object> snapshot = reservationQueueService.getQueueSnapshot(examId);
        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/{examId}/admit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ExamReservation> admitStudent(
            @PathVariable Long examId,
            Principal principal) {
        ExamReservation reservation = reservationQueueService.admitStudent(examId, principal.getName());
        return ResponseEntity.ok(reservation);
    }

    @GetMapping("/{examId}/can-enter")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Boolean> canEnterExam(
            @PathVariable Long examId,
            Principal principal) {
        boolean canEnter = reservationQueueService.canEnterExam(examId, principal.getName());
        return ResponseEntity.ok(canEnter);
    }

    @PostMapping("/{examId}/complete")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> completeReservation(
            @PathVariable Long examId,
            Principal principal) {
        reservationQueueService.completeReservation(examId, principal.getName());
        return ResponseEntity.ok().build();
    }
}
