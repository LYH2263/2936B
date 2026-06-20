package com.exam;

import com.exam.entity.Exam;
import com.exam.entity.ExamReservation;
import com.exam.entity.User;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamReservationRepository;
import com.exam.repository.UserRepository;
import com.exam.service.ReservationQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ReservationQueueService reservationQueueService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExamReservationRepository reservationRepository;

    private Exam testExam;
    private List<User> testStudents;

    @BeforeEach
    void setUp() {
        testExam = new Exam();
        testExam.setTitle("高并发测试考试");
        testExam.setCourse("测试课程");
        testExam.setDuration(60);
        testExam.setState("PUBLISHED");
        testExam.setReservationEnabled(true);
        testExam.setMaxConcurrentUsers(50);
        testExam.setAdmissionTimeout(15);
        testExam.setTimeSlotDuration(60);

        User creator = new User();
        creator.setUsername("teacher_test");
        creator.setPassword("password");
        creator.setRole("TEACHER");
        creator.setFullName("测试教师");
        creator = userRepository.save(creator);
        testExam.setCreator(creator);

        testExam = examRepository.save(testExam);

        testStudents = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            User student = new User();
            student.setUsername("student_" + i);
            student.setPassword("password");
            student.setRole("STUDENT");
            student.setFullName("学生" + i);
            testStudents.add(userRepository.save(student));
        }
    }

    @Test
    @Transactional
    @Rollback
    void testConcurrentReservation_NoOversell() throws InterruptedException {
        int threadCount = 60;
        int maxCapacity = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    reservationQueueService.createReservation(
                            testExam.getId(),
                            null,
                            testStudents.get(index).getUsername()
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("Student " + index + " failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        List<ExamReservation> allReservations = reservationRepository.findActiveReservationsByExamId(testExam.getId());

        System.out.println("=== 并发预约测试结果 ===");
        System.out.println("总请求数: " + threadCount);
        System.out.println("成功数: " + successCount.get());
        System.out.println("失败数: " + failCount.get());
        System.out.println("总预约记录数: " + allReservations.size());

        long confirmedCount = allReservations.stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .count();
        long pendingCount = allReservations.stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .count();

        System.out.println("已确认(CONFIRMED): " + confirmedCount);
        System.out.println("排队中(PENDING): " + pendingCount);

        assertEquals(threadCount, allReservations.size(), "所有学生都应有预约记录");
        assertEquals(maxCapacity, confirmedCount, "确认人数不应超过容量限制");
        assertEquals(threadCount - maxCapacity, pendingCount, "超出容量的学生应在排队中");
        assertTrue(confirmedCount <= maxCapacity, "确认人数绝对不能超过容量");
    }

    @Test
    @Transactional
    @Rollback
    void testFIFOQueueOrder() {
        for (int i = 0; i < 55; i++) {
            reservationQueueService.createReservation(
                    testExam.getId(),
                    null,
                    testStudents.get(i).getUsername()
            );
        }

        List<ExamReservation> pendingQueue = reservationRepository.findPendingQueueByExamId(testExam.getId());

        System.out.println("=== FIFO队列测试 ===");
        for (int i = 0; i < pendingQueue.size(); i++) {
            ExamReservation r = pendingQueue.get(i);
            System.out.printf("位置 %d: 学生 %s, 创建时间 %s%n",
                    i + 1, r.getStudent().getUsername(), r.getCreatedAt());
        }

        for (int i = 0; i < pendingQueue.size() - 1; i++) {
            LocalDateTime currentTime = pendingQueue.get(i).getCreatedAt();
            LocalDateTime nextTime = pendingQueue.get(i + 1).getCreatedAt();
            assertTrue(currentTime.isBefore(nextTime) || currentTime.equals(nextTime),
                    "队列应按创建时间升序排列");
        }

        for (int i = 0; i < pendingQueue.size(); i++) {
            assertEquals(i + 1, pendingQueue.get(i).getQueuePosition(),
                    "排队位置应从1开始连续编号");
        }
    }

    @Test
    @Transactional
    @Rollback
    void testTimeoutRelease_AutoPromotion() throws InterruptedException {
        System.out.println("=== 超时释放测试 ===");

        for (int i = 0; i < 52; i++) {
            reservationQueueService.createReservation(
                    testExam.getId(),
                    null,
                    testStudents.get(i).getUsername()
            );
        }

        List<ExamReservation> initialPending = reservationRepository.findPendingQueueByExamId(testExam.getId());
        System.out.println("初始排队人数: " + initialPending.size());
        assertEquals(2, initialPending.size(), "应有2人在排队");

        List<ExamReservation> confirmed = reservationRepository.findActiveReservationsByExamId(testExam.getId())
                .stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .limit(2)
                .toList();

        System.out.println("模拟2个预约超时...");
        for (ExamReservation r : confirmed) {
            r.setExpiredAt(LocalDateTime.now().minusMinutes(1));
            r.setConfirmedAt(LocalDateTime.now().minusMinutes(16));
            reservationRepository.save(r);
        }

        reservationQueueService.processExpiredReservations();
        reservationQueueService.processQueue(testExam.getId());

        List<ExamReservation> afterExpiredPending = reservationRepository.findPendingQueueByExamId(testExam.getId());
        List<ExamReservation> afterExpiredConfirmed = reservationRepository.findActiveReservationsByExamId(testExam.getId())
                .stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .toList();

        long expiredCount = reservationRepository.findActiveReservationsByExamId(testExam.getId())
                .stream()
                .filter(r -> "EXPIRED".equals(r.getStatus()))
                .count();

        System.out.println("超时后排队人数: " + afterExpiredPending.size());
        System.out.println("超时后确认人数: " + afterExpiredConfirmed.size());
        System.out.println("超时人数: " + expiredCount);

        assertEquals(0, afterExpiredPending.size(), "超时释放后应无人排队");
        assertEquals(50, afterExpiredConfirmed.size(), "应自动递补至满员");
        assertEquals(2, expiredCount, "应有2人超时");

        List<String> originallyPendingUsernames = initialPending.stream()
                .map(r -> r.getStudent().getUsername())
                .toList();

        List<String> newConfirmedUsernames = afterExpiredConfirmed.stream()
                .filter(r -> originallyPendingUsernames.contains(r.getStudent().getUsername()))
                .map(r -> r.getStudent().getUsername())
                .toList();

        System.out.println("自动递补的学生: " + newConfirmedUsernames);
        assertEquals(originallyPendingUsernames.size(), newConfirmedUsernames.size(),
                "原本排队的学生应全部自动递补");
    }

    @Test
    @Transactional
    @Rollback
    void testCancelReservation_PromotesNextInQueue() {
        System.out.println("=== 取消预约测试 ===");

        for (int i = 0; i < 52; i++) {
            reservationQueueService.createReservation(
                    testExam.getId(),
                    null,
                    testStudents.get(i).getUsername()
            );
        }

        assertEquals(2, reservationRepository.findPendingQueueByExamId(testExam.getId()).size());

        ExamReservation toCancel = reservationRepository.findActiveReservationsByExamId(testExam.getId())
                .stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .findFirst()
                .orElseThrow();

        System.out.println("取消学生: " + toCancel.getStudent().getUsername());
        reservationQueueService.cancelReservation(testExam.getId(), toCancel.getStudent().getUsername());

        assertEquals(1, reservationRepository.findPendingQueueByExamId(testExam.getId()).size(),
                "取消后应只剩1人排队");

        long confirmedCount = reservationRepository.findActiveReservationsByExamId(testExam.getId())
                .stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .count();

        assertEquals(50, confirmedCount, "取消后应自动递补，保持50人确认");
    }

    @Test
    @Transactional
    @Rollback
    void testCompleteReservation_ReleasesSlot() {
        System.out.println("=== 完成考试释放名额测试 ===");

        for (int i = 0; i < 52; i++) {
            reservationQueueService.createReservation(
                    testExam.getId(),
                    null,
                    testStudents.get(i).getUsername()
            );
        }

        ExamReservation toAdmit = reservationRepository.findActiveReservationsByExamId(testExam.getId())
                .stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .findFirst()
                .orElseThrow();

        reservationQueueService.admitStudent(testExam.getId(), toAdmit.getStudent().getUsername());
        reservationQueueService.completeReservation(testExam.getId(), toAdmit.getStudent().getUsername());

        ExamReservation completed = reservationRepository.findById(toAdmit.getId()).orElseThrow();
        assertEquals("COMPLETED", completed.getStatus());

        long confirmedCount = reservationRepository.findActiveReservationsByExamId(testExam.getId())
                .stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .count();

        assertEquals(50, confirmedCount, "完成后应自动递补，保持50人确认");
        assertEquals(1, reservationRepository.findPendingQueueByExamId(testExam.getId()).size(),
                "原本排队2人，递补1人，剩1人排队");
    }

    @Test
    @Transactional
    @Rollback
    void testNormalExam_NoReservationRequired() {
        System.out.println("=== 普通考试不受影响测试 ===");

        Exam normalExam = new Exam();
        normalExam.setTitle("普通考试");
        normalExam.setCourse("测试课程");
        normalExam.setDuration(60);
        normalExam.setState("PUBLISHED");
        normalExam.setReservationEnabled(false);
        normalExam.setCreator(testExam.getCreator());
        normalExam = examRepository.save(normalExam);

        boolean canEnter = reservationQueueService.canEnterExam(
                normalExam.getId(),
                testStudents.get(0).getUsername()
        );

        assertTrue(canEnter, "普通考试应无需预约即可进入");

        ExamReservation reservation = null;
        try {
            reservation = reservationQueueService.createReservation(
                    normalExam.getId(),
                    null,
                    testStudents.get(0).getUsername()
            );
            fail("普通考试应不能创建预约");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("does not require reservation"),
                    "普通考试预约应抛出正确异常");
        }

        assertNull(reservation, "普通考试不应创建预约记录");
    }

    @Test
    @Transactional
    @Rollback
    void testHighConcurrency_60vs50_AcceptanceTest() throws InterruptedException {
        System.out.println("\n=== 验收测试: 60人同时预约50名额 ===");

        int totalStudents = 60;
        int capacity = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalStudents);
        ExecutorService executor = Executors.newFixedThreadPool(totalStudents);

        for (int i = 0; i < totalStudents; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    reservationQueueService.createReservation(
                            testExam.getId(),
                            null,
                            testStudents.get(index).getUsername()
                    );
                } catch (Exception e) {
                    System.err.println("Student " + index + " error: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        List<ExamReservation> allReservations = reservationRepository.findActiveReservationsByExamId(testExam.getId());
        List<ExamReservation> pendingQueue = reservationRepository.findPendingQueueByExamId(testExam.getId());

        long confirmed = allReservations.stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .count();
        long pending = allReservations.stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .count();

        System.out.println("测试耗时: " + (endTime - startTime) + "ms");
        System.out.println("总预约数: " + allReservations.size());
        System.out.println("已确认: " + confirmed + "人 (容量: " + capacity + ")");
        System.out.println("排队中: " + pending + "人");

        if (!pendingQueue.isEmpty()) {
            ExamReservation firstPending = pendingQueue.get(0);
            System.out.println("\n第51人 (排队第1位):");
            System.out.println("  学生: " + firstPending.getStudent().getUsername());
            System.out.println("  排队序号: " + firstPending.getQueuePosition());
            System.out.println("  状态: " + firstPending.getStatus());
        }

        assertEquals(capacity, confirmed, "✅ 验收通过: 恰好50人获得确认");
        assertEquals(totalStudents - capacity, pending, "✅ 验收通过: 10人在排队中");
        assertEquals(Integer.valueOf(1), pendingQueue.isEmpty() ? null : pendingQueue.get(0).getQueuePosition(),
                "✅ 验收通过: 第51人排队序号为1");
        assertTrue((endTime - startTime) < 5000, "✅ 验收通过: 60并发请求在5秒内完成");

        System.out.println("\n=== 模拟超时自动递补 ===");
        List<ExamReservation> toExpire = allReservations.stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .limit(3)
                .toList();

        for (ExamReservation r : toExpire) {
            r.setExpiredAt(LocalDateTime.now().minusMinutes(1));
            reservationRepository.save(r);
        }

        reservationQueueService.processExpiredReservations();

        List<ExamReservation> afterPromotion = reservationRepository.findActiveReservationsByExamId(testExam.getId());
        long newConfirmed = afterPromotion.stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()))
                .count();
        long newPending = afterPromotion.stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .count();

        System.out.println("超时3人后:");
        System.out.println("  已确认: " + newConfirmed + "人");
        System.out.println("  排队中: " + newPending + "人");

        assertEquals(capacity, newConfirmed, "✅ 验收通过: 超时后自动递补至满员");
        assertEquals(totalStudents - capacity, newPending,
                "✅ 验收通过: 自动递补后排队人数减少3人");

        System.out.println("\n🎉 所有验收测试通过!");
    }
}
