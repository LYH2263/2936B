package com.exam.service;

import com.exam.entity.*;
import com.exam.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LearningAlertService {

    private static final Logger log = LoggerFactory.getLogger(LearningAlertService.class);

    private static final double PASS_RATE_THRESHOLD = 0.6;
    private static final int CONSECUTIVE_FAIL_COUNT = 2;
    private static final double KNOWLEDGE_POINT_RATE_THRESHOLD = 0.4;
    private static final int KNOWLEDGE_MIN_QUESTIONS = 3;
    private static final int NO_EXAM_DAYS_THRESHOLD = 30;
    private static final int STATISTICS_WINDOW_DAYS = 60;

    private final LearningAlertRepository alertRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public LearningAlertService(LearningAlertRepository alertRepository,
                                 SubmissionRepository submissionRepository,
                                 SubmissionAnswerRepository submissionAnswerRepository,
                                 UserRepository userRepository,
                                 ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.submissionRepository = submissionRepository;
        this.submissionAnswerRepository = submissionAnswerRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Integer> runFullScan() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("CONSECUTIVE_LOW_SCORE", 0);
        stats.put("KNOWLEDGE_POINT_LOW", 0);
        stats.put("LONG_TIME_NO_EXAM", 0);

        List<User> students = userRepository.findByRole("STUDENT");
        log.info("Starting learning alert scan for {} students", students.size());

        for (User student : students) {
            try {
                int c1 = scanConsecutiveLowScore(student);
                int c2 = scanKnowledgePointLow(student);
                int c3 = scanLongTimeNoExam(student);
                stats.merge("CONSECUTIVE_LOW_SCORE", c1, Integer::sum);
                stats.merge("KNOWLEDGE_POINT_LOW", c2, Integer::sum);
                stats.merge("LONG_TIME_NO_EXAM", c3, Integer::sum);
            } catch (Exception e) {
                log.error("Error scanning student {}: {}", student.getId(), e.getMessage());
            }
        }

        log.info("Alert scan completed: {}", stats);
        return stats;
    }

    @Transactional
    public Map<String, Integer> scanStudent(Long studentId) {
        Map<String, Integer> stats = new HashMap<>();
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        stats.put("CONSECUTIVE_LOW_SCORE", scanConsecutiveLowScore(student));
        stats.put("KNOWLEDGE_POINT_LOW", scanKnowledgePointLow(student));
        stats.put("LONG_TIME_NO_EXAM", scanLongTimeNoExam(student));
        return stats;
    }

    private int scanConsecutiveLowScore(User student) {
        List<Submission> submissions = submissionRepository.findLastNByStudent(student.getId(), 10);
        if (submissions.isEmpty()) return 0;

        List<Submission> submittedSorted = submissions.stream()
                .filter(s -> s.getState() != null && s.getState().equals("SUBMITTED"))
                .sorted(Comparator.comparing(Submission::getEndTime).reversed())
                .collect(Collectors.toList());

        if (submittedSorted.size() < CONSECUTIVE_FAIL_COUNT) return 0;

        int newAlerts = 0;
        int consecutiveFails = 0;
        List<Map<String, Object>> scoreTrend = new ArrayList<>();

        for (Submission sub : submittedSorted) {
            Exam exam = sub.getExam();
            if (exam == null) continue;

            Integer totalScore = calculateExamTotalScore(sub);
            Map<String, Object> trendItem = new LinkedHashMap<>();
            trendItem.put("examId", exam.getId());
            trendItem.put("examTitle", exam.getTitle());
            trendItem.put("score", sub.getScore());
            trendItem.put("totalScore", totalScore);
            trendItem.put("endTime", sub.getEndTime() != null ? sub.getEndTime().toString() : null);
            scoreTrend.add(trendItem);

            if (totalScore != null && totalScore > 0 && sub.getScore() != null) {
                double rate = (double) sub.getScore() / totalScore;
                if (rate < PASS_RATE_THRESHOLD) {
                    consecutiveFails++;
                } else {
                    consecutiveFails = 0;
                }
            }
        }

        if (consecutiveFails >= CONSECUTIVE_FAIL_COUNT) {
            boolean alreadyExists = alertRepository
                    .findRecentUnresolvedByStudentAndType(student.getId(), "CONSECUTIVE_LOW_SCORE", LocalDateTime.now().minusDays(1))
                    .size() > 0;

            if (!alreadyExists) {
                LearningAlert alert = new LearningAlert();
                alert.setStudent(student);
                alert.setAlertType("CONSECUTIVE_LOW_SCORE");
                alert.setSeverity("HIGH");
                alert.setTitle("连续两次考试低于及格线");

                String lastTwoExams = scoreTrend.stream()
                        .limit(2)
                        .map(t -> t.get("examTitle") + "(" + t.get("score") + "/" + t.get("totalScore") + ")")
                        .collect(Collectors.joining("、"));

                alert.setDetail("学生「" + student.getFullName() + "」最近连续 " + consecutiveFails +
                        " 次考试成绩低于及格线（60%），最近两次：" + lastTwoExams);
                alert.setIsResolved(false);

                Map<String, Object> related = new LinkedHashMap<>();
                related.put("consecutiveCount", consecutiveFails);
                related.put("scoreTrend", scoreTrend);
                related.put("threshold", PASS_RATE_THRESHOLD);
                try {
                    alert.setRelatedData(objectMapper.writeValueAsString(related));
                } catch (JsonProcessingException e) {
                    alert.setRelatedData("{}");
                }
                alertRepository.save(alert);
                newAlerts++;
            }
        }

        return newAlerts;
    }

    private Integer calculateExamTotalScore(Submission submission) {
        if (submission.getExamTotalScore() != null) return submission.getExamTotalScore();
        if (submission.getAnswers() != null && !submission.getAnswers().isEmpty()) {
            return submission.getAnswers().stream()
                    .map(a -> a.getQuestion() != null ? a.getQuestion().getDefaultScore() : 0)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).sum();
        }
        return 100;
    }

    private int scanKnowledgePointLow(User student) {
        LocalDateTime since = LocalDateTime.now().minusDays(STATISTICS_WINDOW_DAYS);
        List<Object[]> kpStats = submissionAnswerRepository
                .getKnowledgePointStatsByStudent(student.getId(), since, KNOWLEDGE_MIN_QUESTIONS);

        int newAlerts = 0;
        for (Object[] row : kpStats) {
            String knowledgePoint = (String) row[0];
            Long totalCount = ((Number) row[1]).longValue();
            Double correctRate = ((Number) row[2]).doubleValue();

            if (correctRate < KNOWLEDGE_POINT_RATE_THRESHOLD && totalCount >= KNOWLEDGE_MIN_QUESTIONS) {
                String dedupKey = student.getId() + "_" + knowledgePoint;
                boolean alreadyExists = alertRepository
                        .findRecentUnresolvedByStudentAndType(student.getId(), "KNOWLEDGE_POINT_LOW", LocalDateTime.now().minusDays(1))
                        .stream()
                        .anyMatch(a -> a.getRelatedData() != null && a.getRelatedData().contains("\"knowledgePoint\":\"" + knowledgePoint + "\""));

                if (!alreadyExists) {
                    List<SubmissionAnswer> relatedAnswers = submissionAnswerRepository
                            .findByStudentAndKnowledgePoint(student.getId(), knowledgePoint);

                    LearningAlert alert = new LearningAlert();
                    alert.setStudent(student);
                    alert.setAlertType("KNOWLEDGE_POINT_LOW");
                    alert.setSeverity(correctRate < 0.2 ? "HIGH" : "MEDIUM");
                    alert.setTitle("知识点正确率低于40%");
                    alert.setDetail("学生「" + student.getFullName() + "」在知识点「" + knowledgePoint +
                            "」的正确率仅为 " + String.format("%.1f%%", correctRate * 100) +
                            "，累计 " + totalCount + " 道相关题目");
                    alert.setIsResolved(false);

                    Map<String, Object> related = new LinkedHashMap<>();
                    related.put("knowledgePoint", knowledgePoint);
                    related.put("totalQuestions", totalCount);
                    related.put("correctRate", correctRate);
                    related.put("threshold", KNOWLEDGE_POINT_RATE_THRESHOLD);

                    List<Map<String, Object>> questionDetails = new ArrayList<>();
                    for (SubmissionAnswer sa : relatedAnswers.stream().limit(10).collect(Collectors.toList())) {
                        Map<String, Object> qd = new LinkedHashMap<>();
                        qd.put("questionId", sa.getQuestion().getId());
                        qd.put("questionContent", truncate(sa.getQuestion().getContent(), 80));
                        qd.put("score", sa.getScore());
                        qd.put("maxScore", sa.getQuestion().getDefaultScore());
                        qd.put("examId", sa.getSubmission() != null && sa.getSubmission().getExam() != null ?
                                sa.getSubmission().getExam().getId() : null);
                        qd.put("endTime", sa.getSubmission() != null && sa.getSubmission().getEndTime() != null ?
                                sa.getSubmission().getEndTime().toString() : null);
                        questionDetails.add(qd);
                    }
                    related.put("questionDetails", questionDetails);

                    try {
                        alert.setRelatedData(objectMapper.writeValueAsString(related));
                    } catch (JsonProcessingException e) {
                        alert.setRelatedData("{}");
                    }
                    alertRepository.save(alert);
                    newAlerts++;
                }
            }
        }
        return newAlerts;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("<[^>]*>", "").trim();
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private int scanLongTimeNoExam(User student) {
        Optional<LocalDateTime> lastExamTime = submissionRepository.findLastExamTimeByStudent(student.getId());
        long daysSinceLastExam;

        if (lastExamTime.isEmpty()) {
            long daysSinceRegister = ChronoUnit.DAYS.between(
                    student.getCreatedAt() != null ? student.getCreatedAt() : LocalDateTime.now().minusDays(NO_EXAM_DAYS_THRESHOLD + 1),
                    LocalDateTime.now());
            daysSinceLastExam = daysSinceRegister;
        } else {
            daysSinceLastExam = ChronoUnit.DAYS.between(lastExamTime.get(), LocalDateTime.now());
        }

        if (daysSinceLastExam >= NO_EXAM_DAYS_THRESHOLD) {
            boolean alreadyExists = alertRepository
                    .findRecentUnresolvedByStudentAndType(student.getId(), "LONG_TIME_NO_EXAM", LocalDateTime.now().minusDays(7))
                    .size() > 0;

            if (!alreadyExists) {
                LearningAlert alert = new LearningAlert();
                alert.setStudent(student);
                alert.setAlertType("LONG_TIME_NO_EXAM");
                alert.setSeverity(daysSinceLastExam >= 60 ? "HIGH" : "MEDIUM");
                alert.setTitle("长期未参加考试");
                alert.setDetail("学生「" + student.getFullName() + "」已 " + daysSinceLastExam + " 天未参加考试" +
                        (lastExamTime.isPresent() ? "（上次考试：" + lastExamTime.get().toLocalDate() + "）" : "（从未参加考试）"));
                alert.setIsResolved(false);

                Map<String, Object> related = new LinkedHashMap<>();
                related.put("daysSinceLastExam", daysSinceLastExam);
                related.put("lastExamTime", lastExamTime.map(Object::toString).orElse(null));
                related.put("threshold", NO_EXAM_DAYS_THRESHOLD);
                related.put("registeredAt", student.getCreatedAt() != null ? student.getCreatedAt().toString() : null);

                try {
                    alert.setRelatedData(objectMapper.writeValueAsString(related));
                } catch (JsonProcessingException e) {
                    alert.setRelatedData("{}");
                }
                alertRepository.save(alert);
                return 1;
            }
        }
        return 0;
    }
}
