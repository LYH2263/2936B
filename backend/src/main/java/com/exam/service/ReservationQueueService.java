package com.exam.service;

import com.exam.dto.QueuePositionDTO;
import com.exam.dto.TimeSlotDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamReservation;
import com.exam.entity.ExamTimeSlot;
import com.exam.entity.User;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamReservationRepository;
import com.exam.repository.ExamTimeSlotRepository;
import com.exam.repository.UserRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ReservationQueueService {

    private final ExamRepository examRepository;
    private final ExamReservationRepository reservationRepository;
    private final ExamTimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    private final Map<Long, Object> examLocks = new ConcurrentHashMap<>();

    public ReservationQueueService(ExamRepository examRepository,
                                    ExamReservationRepository reservationRepository,
                                    ExamTimeSlotRepository timeSlotRepository,
                                    UserRepository userRepository,
                                    SimpMessagingTemplate messagingTemplate,
                                    NotificationService notificationService) {
        this.examRepository = examRepository;
        this.reservationRepository = reservationRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
    }

    private Object getExamLock(Long examId) {
        return examLocks.computeIfAbsent(examId, k -> new Object());
    }

    @Transactional
    public ExamReservation createReservation(Long examId, Long timeSlotId, String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        if (!Boolean.TRUE.equals(exam.getReservationEnabled())) {
            throw new RuntimeException("This exam does not require reservation");
        }

        Optional<ExamReservation> existing = reservationRepository.findByExamIdAndStudentId(examId, student.getId());
        if (existing.isPresent()) {
            ExamReservation r = existing.get();
            if (!"CANCELLED".equals(r.getStatus()) && !"EXPIRED".equals(r.getStatus()) && !"COMPLETED".equals(r.getStatus())) {
                return r;
            }
        }

        ExamReservation reservation = new ExamReservation();
        reservation.setExam(exam);
        reservation.setStudent(student);
        reservation.setStatus("PENDING");

        if (timeSlotId != null) {
            ExamTimeSlot slot = timeSlotRepository.findByIdWithLock(timeSlotId)
                    .orElseThrow(() -> new RuntimeException("Time slot not found"));
            if (slot.getReservedCount() >= slot.getCapacity()) {
                throw new RuntimeException("Selected time slot is full");
            }
            reservation.setTimeSlot(slot);
        }

        reservation = reservationRepository.save(reservation);

        updateQueuePositions(examId);
        processQueue(examId);

        return reservation;
    }

    @Transactional
    public void processQueue(Long examId) {
        synchronized (getExamLock(examId)) {
            Exam exam = examRepository.findById(examId).orElseThrow();
            if (!Boolean.TRUE.equals(exam.getReservationEnabled())) return;

            int maxConcurrent = exam.getMaxConcurrentUsers() != null ? exam.getMaxConcurrentUsers() : 50;
            int activeCount = reservationRepository.countActiveReservationsForExam(examId);
            int availableSlots = maxConcurrent - activeCount;

            if (availableSlots <= 0) return;

            List<ExamReservation> pendingQueue = reservationRepository.findPendingQueueByExamId(examId);

            for (int i = 0; i < availableSlots && i < pendingQueue.size(); i++) {
                ExamReservation reservation = pendingQueue.get(i);
                confirmReservation(reservation, exam);
            }
        }
    }

    private void confirmReservation(ExamReservation reservation, Exam exam) {
        ExamTimeSlot slot = getOrAssignTimeSlot(reservation, exam);
        if (slot == null) return;

        try {
            if (slot.getReservedCount() >= slot.getCapacity()) {
                return;
            }

            slot.setReservedCount(slot.getReservedCount() + 1);
            timeSlotRepository.save(slot);

            reservation.setTimeSlot(slot);
            reservation.setStatus("CONFIRMED");
            reservation.setQueuePosition(null);
            reservation.setConfirmedAt(LocalDateTime.now());
            int timeout = exam.getAdmissionTimeout() != null ? exam.getAdmissionTimeout() : 15;
            reservation.setExpiredAt(LocalDateTime.now().plusMinutes(timeout));

            reservationRepository.save(reservation);

            broadcastQueueUpdate(exam.getId());

            notificationService.createNotification(
                    reservation.getStudent(),
                    "考试入场资格已确认",
                    String.format("您已获得「%s」的入场资格，请在%d分钟内进入考场，否则资格将作废。",
                            exam.getTitle(), timeout),
                    "RESERVATION_CONFIRMED",
                    exam.getId()
            );

            messagingTemplate.convertAndSendToUser(
                    reservation.getStudent().getUsername(),
                    "/queue/reservation",
                    convertToDTO(reservation, exam)
            );

        } catch (OptimisticLockException e) {
            throw new RuntimeException("High concurrency, please try again later");
        }
    }

    private ExamTimeSlot getOrAssignTimeSlot(ExamReservation reservation, Exam exam) {
        if (reservation.getTimeSlot() != null) {
            return timeSlotRepository.findByIdWithLock(reservation.getTimeSlot().getId()).orElse(null);
        }

        List<ExamTimeSlot> availableSlots = timeSlotRepository.findAvailableSlotsForExam(
                exam.getId(), LocalDateTime.now());

        if (availableSlots.isEmpty()) {
            ExamTimeSlot newSlot = createNewTimeSlot(exam);
            return timeSlotRepository.save(newSlot);
        }

        return availableSlots.get(0);
    }

    private ExamTimeSlot createNewTimeSlot(Exam exam) {
        ExamTimeSlot slot = new ExamTimeSlot();
        slot.setExam(exam);
        slot.setStartTime(LocalDateTime.now());
        int duration = exam.getTimeSlotDuration() != null ? exam.getTimeSlotDuration() : exam.getDuration();
        slot.setEndTime(LocalDateTime.now().plusMinutes(duration));
        slot.setCapacity(exam.getMaxConcurrentUsers() != null ? exam.getMaxConcurrentUsers() : 50);
        slot.setReservedCount(0);
        slot.setActiveCount(0);
        return slot;
    }

    @Transactional
    public ExamReservation admitStudent(Long examId, String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        ExamReservation reservation = reservationRepository.findByExamIdAndStudentId(examId, student.getId())
                .orElseThrow(() -> new RuntimeException("No reservation found"));

        if ("CONFIRMED".equals(reservation.getStatus())) {
            if (LocalDateTime.now().isAfter(reservation.getExpiredAt())) {
                expireReservation(reservation);
                throw new RuntimeException("Reservation has expired");
            }

            ExamTimeSlot slot = reservation.getTimeSlot();
            if (slot != null) {
                slot.setActiveCount(slot.getActiveCount() + 1);
                timeSlotRepository.save(slot);
            }

            reservation.setStatus("ADMITTED");
            reservation.setAdmittedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            processQueue(examId);
            return reservation;
        } else if ("ADMITTED".equals(reservation.getStatus())) {
            return reservation;
        } else {
            throw new RuntimeException("Reservation is not confirmed yet");
        }
    }

    @Transactional
    public void expireReservation(ExamReservation reservation) {
        reservation.setStatus("EXPIRED");

        ExamTimeSlot slot = reservation.getTimeSlot();
        if (slot != null) {
            slot.setReservedCount(Math.max(0, slot.getReservedCount() - 1));
            timeSlotRepository.save(slot);
        }

        reservationRepository.save(reservation);
        processQueue(reservation.getExam().getId());
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void processExpiredReservations() {
        List<ExamReservation> expired = reservationRepository.findExpiredReservations(LocalDateTime.now());
        for (ExamReservation r : expired) {
            expireReservation(r);
        }
    }

    @Transactional
    public void cancelReservation(Long examId, String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        ExamReservation reservation = reservationRepository.findByExamIdAndStudentId(examId, student.getId())
                .orElseThrow(() -> new RuntimeException("No reservation found"));

        if ("PENDING".equals(reservation.getStatus()) || "CONFIRMED".equals(reservation.getStatus())) {
            reservation.setStatus("CANCELLED");

            ExamTimeSlot slot = reservation.getTimeSlot();
            if (slot != null && "CONFIRMED".equals(reservation.getStatus())) {
                slot.setReservedCount(Math.max(0, slot.getReservedCount() - 1));
                timeSlotRepository.save(slot);
            }

            reservationRepository.save(reservation);
            updateQueuePositions(examId);
            processQueue(examId);
        }
    }

    private void updateQueuePositions(Long examId) {
        List<ExamReservation> pendingQueue = reservationRepository.findPendingQueueByExamId(examId);
        for (int i = 0; i < pendingQueue.size(); i++) {
            ExamReservation r = pendingQueue.get(i);
            r.setQueuePosition(i + 1);
            reservationRepository.save(r);
        }
        broadcastQueueUpdate(examId);
    }

    public QueuePositionDTO getQueuePosition(Long examId, String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        Exam exam = examRepository.findById(examId).orElseThrow();
        ExamReservation reservation = reservationRepository.findByExamIdAndStudentId(examId, student.getId())
                .orElse(null);

        if (reservation == null) {
            return null;
        }

        return convertToDTO(reservation, exam);
    }

    private QueuePositionDTO convertToDTO(ExamReservation reservation, Exam exam) {
        QueuePositionDTO dto = new QueuePositionDTO();
        dto.setReservationId(reservation.getId());
        dto.setExamId(exam.getId());
        dto.setExamTitle(exam.getTitle());
        dto.setStatus(reservation.getStatus());
        dto.setPosition(reservation.getQueuePosition());
        dto.setAdmissionTimeout(exam.getAdmissionTimeout() != null ? exam.getAdmissionTimeout() : 15);
        dto.setConfirmedAt(reservation.getConfirmedAt());
        dto.setExpiredAt(reservation.getExpiredAt());

        if (reservation.getExpiredAt() != null && "CONFIRMED".equals(reservation.getStatus())) {
            dto.setSecondsUntilExpiry(Duration.between(LocalDateTime.now(), reservation.getExpiredAt()).getSeconds());
        }

        if (reservation.getTimeSlot() != null) {
            dto.setTimeSlotId(reservation.getTimeSlot().getId());
            dto.setTimeSlotStart(reservation.getTimeSlot().getStartTime());
            dto.setTimeSlotEnd(reservation.getTimeSlot().getEndTime());
        }

        if ("PENDING".equals(reservation.getStatus())) {
            List<ExamReservation> pendingQueue = reservationRepository.findPendingQueueByExamId(exam.getId());
            dto.setTotalWaiting(pendingQueue.size());

            int activeCount = reservationRepository.countActiveReservationsForExam(exam.getId());
            int maxConcurrent = exam.getMaxConcurrentUsers() != null ? exam.getMaxConcurrentUsers() : 50;
            int turnoverRate = maxConcurrent / 5;
            int position = reservation.getQueuePosition() != null ? reservation.getQueuePosition() : pendingQueue.size();
            int estimatedWait = (int) Math.ceil((double) position / turnoverRate) * exam.getDuration() / 3;
            dto.setEstimatedWaitMinutes(Math.max(1, estimatedWait));
        }

        return dto;
    }

    public List<TimeSlotDTO> getTimeSlots(Long examId) {
        List<ExamTimeSlot> slots = timeSlotRepository.findByExamIdOrderByStartTimeAsc(examId);
        return slots.stream().map(slot -> {
            TimeSlotDTO dto = new TimeSlotDTO();
            dto.setId(slot.getId());
            dto.setStartTime(slot.getStartTime());
            dto.setEndTime(slot.getEndTime());
            dto.setCapacity(slot.getCapacity());
            dto.setReservedCount(slot.getReservedCount());
            dto.setAvailable(slot.getReservedCount() < slot.getCapacity());
            return dto;
        }).collect(Collectors.toList());
    }

    private void broadcastQueueUpdate(Long examId) {
        messagingTemplate.convertAndSend("/topic/exam/" + examId + "/queue", getQueueSnapshot(examId));
    }

    public Map<String, Object> getQueueSnapshot(Long examId) {
        Map<String, Object> snapshot = new HashMap<>();
        Exam exam = examRepository.findById(examId).orElseThrow();

        int maxConcurrent = exam.getMaxConcurrentUsers() != null ? exam.getMaxConcurrentUsers() : 50;
        int activeCount = reservationRepository.countActiveReservationsForExam(examId);
        List<ExamReservation> pendingQueue = reservationRepository.findPendingQueueByExamId(examId);

        snapshot.put("examId", examId);
        snapshot.put("maxConcurrent", maxConcurrent);
        snapshot.put("activeCount", activeCount);
        snapshot.put("availableSlots", Math.max(0, maxConcurrent - activeCount));
        snapshot.put("pendingCount", pendingQueue.size());
        snapshot.put("timestamp", LocalDateTime.now().toString());

        return snapshot;
    }

    @Transactional
    public void completeReservation(Long examId, String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        ExamReservation reservation = reservationRepository.findByExamIdAndStudentId(examId, student.getId())
                .orElseThrow(() -> new RuntimeException("No reservation found"));

        if ("ADMITTED".equals(reservation.getStatus())) {
            reservation.setStatus("COMPLETED");

            ExamTimeSlot slot = reservation.getTimeSlot();
            if (slot != null) {
                slot.setActiveCount(Math.max(0, slot.getActiveCount() - 1));
                timeSlotRepository.save(slot);
            }

            reservationRepository.save(reservation);
            processQueue(examId);
        }
    }

    public boolean canEnterExam(Long examId, String username) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        if (!Boolean.TRUE.equals(exam.getReservationEnabled())) {
            return true;
        }

        User student = userRepository.findByUsername(username).orElseThrow();
        ExamReservation reservation = reservationRepository.findByExamIdAndStudentId(examId, student.getId())
                .orElse(null);

        if (reservation == null) return false;
        if ("EXPIRED".equals(reservation.getStatus()) || "CANCELLED".equals(reservation.getStatus())) return false;
        if ("PENDING".equals(reservation.getStatus())) return false;

        if ("CONFIRMED".equals(reservation.getStatus()) && LocalDateTime.now().isAfter(reservation.getExpiredAt())) {
            return false;
        }

        return true;
    }
}
