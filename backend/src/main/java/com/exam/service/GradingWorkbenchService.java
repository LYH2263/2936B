package com.exam.service;

import com.exam.dto.*;
import com.exam.entity.*;
import com.exam.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GradingWorkbenchService {

    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final UserRepository userRepository;
    private final WrongQuestionBookService wrongQuestionBookService;

    public GradingWorkbenchService(SubmissionAnswerRepository submissionAnswerRepository,
                                   SubmissionRepository submissionRepository,
                                   ExamRepository examRepository,
                                   QuestionRepository questionRepository,
                                   ExamQuestionRepository examQuestionRepository,
                                   UserRepository userRepository,
                                   WrongQuestionBookService wrongQuestionBookService) {
        this.submissionAnswerRepository = submissionAnswerRepository;
        this.submissionRepository = submissionRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.userRepository = userRepository;
        this.wrongQuestionBookService = wrongQuestionBookService;
    }

    public List<Exam> getTeacherExamsWithSubjective(String username) {
        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));
        return examRepository.findExamsWithSubjectiveQuestionsByTeacher(teacher.getId());
    }

    public List<Map<String, Object>> getSubjectiveQuestionsByExam(Long examId) {
        List<Object[]> results = submissionAnswerRepository.findSubjectiveQuestionsByExam(examId);
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> q = new HashMap<>();
            q.put("id", row[0]);
            q.put("content", row[1]);
            q.put("score", row[2]);
            questions.add(q);
        }
        return questions;
    }

    public List<GradingQueueItemDTO> getGradingQueue(Long examId, Long questionId, int limit) {
        List<GradingQueueItemDTO> queue;
        if (questionId != null) {
            queue = submissionAnswerRepository.findUngradedByExamAndQuestion(examId, questionId);
        } else {
            queue = submissionAnswerRepository.findAllUngradedSubjectiveByExam(examId);
        }
        if (limit > 0 && queue.size() > limit) {
            return queue.subList(0, limit);
        }
        return queue;
    }

    public Question getQuestionDetail(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));
    }

    @Transactional
    public BatchGradeResultDTO batchGrade(List<BatchGradeItemDTO> items, String username) {
        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        BatchGradeResultDTO result = new BatchGradeResultDTO();
        int successCount = 0;
        int failedCount = 0;
        List<String> failedMessages = new ArrayList<>();
        Set<Long> updatedSubmissionIds = new HashSet<>();

        for (BatchGradeItemDTO item : items) {
            try {
                SubmissionAnswer sa = submissionAnswerRepository.findById(item.getSubmissionAnswerId())
                        .orElseThrow(() -> new RuntimeException("Answer not found: " + item.getSubmissionAnswerId()));

                if (item.getVersion() != null && !item.getVersion().equals(sa.getVersion())) {
                    throw new RuntimeException("Optimistic lock failure: answer " + item.getSubmissionAnswerId() +
                            " has been modified by another teacher");
                }

                if (item.getScore() == null) {
                    throw new RuntimeException("Score is required for answer " + item.getSubmissionAnswerId());
                }

                ExamQuestion eq = examQuestionRepository.findByExamIdAndQuestionId(
                        sa.getSubmission().getExam().getId(), sa.getQuestion().getId());
                int maxScore = eq != null ? eq.getScore() : (sa.getQuestion().getDefaultScore() != null ? sa.getQuestion().getDefaultScore() : 100);
                if (item.getScore() < 0 || item.getScore() > maxScore) {
                    throw new RuntimeException("Score " + item.getScore() + " out of range [0, " + maxScore + "]");
                }

                Integer previousScore = sa.getScore();
                sa.setScore(item.getScore());
                sa.setTeacherComment(item.getTeacherComment());
                sa.setGrader(teacher);
                sa.setGradedAt(LocalDateTime.now());

                if (item.getScore() != null && previousScore == null) {
                    sa.setGradingTimeSpent(estimateGradingTime());
                }

                submissionAnswerRepository.save(sa);
                updatedSubmissionIds.add(sa.getSubmission().getId());
                successCount++;
            } catch (Exception e) {
                failedCount++;
                failedMessages.add("ID " + item.getSubmissionAnswerId() + ": " + e.getMessage());
            }
        }

        for (Long submissionId : updatedSubmissionIds) {
            updateSubmissionTotalScore(submissionId);
            wrongQuestionBookService.processSubmissionWrongQuestions(submissionId);
        }

        result.setSuccessCount(successCount);
        result.setFailedCount(failedCount);
        result.setFailedMessages(failedMessages);

        return result;
    }

    private int estimateGradingTime() {
        return 30 + new Random().nextInt(60);
    }

    private void updateSubmissionTotalScore(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElseThrow();
        int totalScore = 0;
        for (SubmissionAnswer sa : submission.getAnswers()) {
            if (sa.getScore() != null) {
                totalScore += sa.getScore();
            }
        }
        submission.setScore(totalScore);
        submissionRepository.save(submission);
    }

    public GradingWorkbenchStatsDTO getTeacherStats(String username) {
        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        GradingWorkbenchStatsDTO stats = new GradingWorkbenchStatsDTO();
        long todayCount = submissionAnswerRepository.countTodayGradedByTeacher(teacher.getId(), startOfDay, endOfDay);
        stats.setTodayGradedCount(todayCount);

        Long totalSeconds = submissionAnswerRepository.sumTodayGradingTimeByTeacher(teacher.getId(), startOfDay, endOfDay);
        long totalSec = totalSeconds != null ? totalSeconds : 0L;
        stats.setTodayTotalSeconds(totalSec);

        if (todayCount > 0 && totalSec > 0) {
            stats.setAvgGradingSeconds((double) totalSec / todayCount);
        } else {
            stats.setAvgGradingSeconds(0);
        }

        stats.setTotalPendingCount(submissionAnswerRepository.countTotalUngradedForTeacher(teacher.getId()));

        return stats;
    }

    @Transactional
    public Question updateRubric(Long questionId, String rubricContent) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        question.setAnalysis(rubricContent);
        return questionRepository.save(question);
    }
}
