package com.exam.service;

import com.exam.entity.*;
import com.exam.repository.*;
import com.exam.dto.SubmissionGradeDTO;
import com.exam.dto.StudentStatsDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SubmissionService {
    private final SubmissionRepository submissionRepository;
    private final SubmissionAnswerRepository submissionAnswerRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ExamQuestionRepository examQuestionRepository;

    public SubmissionService(SubmissionRepository submissionRepository, SubmissionAnswerRepository submissionAnswerRepository, ExamRepository examRepository, QuestionRepository questionRepository, UserRepository userRepository, ExamQuestionRepository examQuestionRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionAnswerRepository = submissionAnswerRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.examQuestionRepository = examQuestionRepository;
    }

    @Transactional
    public Submission submitExam(Long examId, Map<Long, String> answers, String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        Exam exam = examRepository.findById(examId).orElseThrow();
        
        Submission submission = new Submission();
        submission.setExam(exam);
        submission.setStudent(student);
        submission.setStartTime(LocalDateTime.now().minusMinutes(30)); // Mock start time
        submission.setEndTime(LocalDateTime.now());
        submission.setState("SUBMITTED");
        
        int totalScore = 0;
        
        // Fetch all questions for this exam to calculate total possible score
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySequenceAsc(examId);
        int examTotalScore = examQuestions.stream().mapToInt(ExamQuestion::getScore).sum();
        submission.setExamTotalScore(examTotalScore);

        Map<Long, ExamQuestion> eqMap = examQuestions.stream().collect(Collectors.toMap(eq -> eq.getQuestion().getId(), eq -> eq));

        submission = submissionRepository.save(submission);

        for (Map.Entry<Long, String> entry : answers.entrySet()) {
            Long questionId = entry.getKey();
            String studentAnswerText = entry.getValue();
            
            Question question = questionRepository.findById(questionId).orElseThrow();
            ExamQuestion eq = eqMap.get(questionId);
            
            SubmissionAnswer answer = new SubmissionAnswer();
            answer.setSubmission(submission);
            answer.setQuestion(question);
            answer.setStudentAnswer(studentAnswerText);
            
            // Auto grading for objective questions
            int score = 0;
            if ("SINGLE".equals(question.getType()) || "JUDGE".equals(question.getType())) {
                if (studentAnswerText != null && studentAnswerText.equals(question.getAnswer())) {
                    score = eq.getScore();
                }
            }
            // Robust multi-choice logic (sort and compare)
            else if ("MULTI".equals(question.getType())) {
                if (studentAnswerText != null && question.getAnswer() != null) {
                    List<String> userOpts = java.util.stream.Stream.of(studentAnswerText.split(",|\\s+"))
                        .map(String::trim).sorted().collect(Collectors.toList());
                    List<String> correctOpts = java.util.stream.Stream.of(question.getAnswer().split(",|\\s+"))
                        .map(String::trim).sorted().collect(Collectors.toList());
                    
                    if (userOpts.equals(correctOpts)) {
                        score = eq.getScore();
                    }
                }
            }
            // Keyword matching for short answer
            else if ("SHORT".equals(question.getType())) {
                 if (studentAnswerText != null && question.getAnswer() != null) {
                     String[] keywords = question.getAnswer().split(";|\\s+"); // Assume keywords separated by ; or space
                     int matches = 0;
                     for (String keyword : keywords) {
                         if (!keyword.isEmpty() && studentAnswerText.contains(keyword)) {
                             matches++;
                         }
                     }
                     if (keywords.length > 0) {
                         // Proportional score
                         score = (int) Math.round((double) matches / keywords.length * eq.getScore());
                     }
                 }
            }
            
            answer.setScore(score);
            totalScore += score;
            submissionAnswerRepository.save(answer);
        }
        
        submission.setScore(totalScore);
        return submissionRepository.save(submission);
    }
    
    public List<Submission> getStudentSubmissions(String username) {
        List<Submission> submissions = submissionRepository.findByStudentUsername(username);
        for (Submission s : submissions) {
            Long examId = s.getExam().getId();
            List<Submission> allSubmissionsForExam = submissionRepository.findByExamId(examId);
            
            // Sort by score descending to find rank
            allSubmissionsForExam.sort((a, b) -> b.getScore().compareTo(a.getScore()));
            
            int rank = 1;
            for (Submission item : allSubmissionsForExam) {
                if (item.getId().equals(s.getId())) break;
                rank++;
            }
            s.setRanking(rank);
            
            // Set exam total score
            Integer sum = examQuestionRepository.sumScoreByExamId(examId);
            s.setExamTotalScore(sum != null ? sum : 0);
        }
        return submissions;
    }

    public List<Submission> getExamSubmissions(Long examId) {
        return submissionRepository.findByExamId(examId);
    }

    public Submission getSubmissionById(Long id) {
        Submission submission = submissionRepository.findById(id).orElseThrow(() -> new RuntimeException("Submission not found"));
        
        // Calculate ranking
        List<Submission> allSubmissionsForExam = submissionRepository.findByExamId(submission.getExam().getId());
        allSubmissionsForExam.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        int rank = 1;
        for (Submission item : allSubmissionsForExam) {
            if (item.getId().equals(submission.getId())) break;
            rank++;
        }
        submission.setRanking(rank);

        // Fetch exam total score
        Integer sum = examQuestionRepository.sumScoreByExamId(submission.getExam().getId());
        submission.setExamTotalScore(sum != null ? sum : 0);

        return submission;
    }

    @Transactional
    public void gradeSubmission(Long submissionId, Map<Long, SubmissionGradeDTO> grades) {
        Submission submission = submissionRepository.findById(submissionId).orElseThrow(() -> new RuntimeException("Submission not found"));
        
        int totalScore = 0;
        for (SubmissionAnswer sa : submission.getAnswers()) {
            SubmissionGradeDTO grade = grades.get(sa.getQuestion().getId());
            if (grade != null) {
                sa.setScore(grade.getScore());
                sa.setTeacherComment(grade.getTeacherComment());
            }
            totalScore += (sa.getScore() != null ? sa.getScore() : 0);
        }
        
        submission.setScore(totalScore);
        submissionRepository.save(submission);
    }

    public StudentStatsDTO getStudentStats(String username) {
        List<Submission> userSubmissions = submissionRepository.findByStudentUsername(username);
        StudentStatsDTO stats = new StudentStatsDTO();
        
        long totalExams = userSubmissions.size();
        stats.setTotalExams(totalExams);
        
        if (totalExams > 0) {
            double totalScorePct = 0;
            int passCount = 0;
            
            for (Submission s : userSubmissions) {
                // Ensure examTotalScore is present
                Integer totalScore = examQuestionRepository.sumScoreByExamId(s.getExam().getId());
                int total = totalScore != null ? totalScore : 0;
                
                if (total > 0) {
                    double pct = (double) s.getScore() / total;
                    totalScorePct += pct;
                    if (pct >= 0.6) passCount++;
                }
            }
            
            stats.setAvgScore(Math.round((totalScorePct / totalExams) * 1000.0) / 10.0); // e.g., 85.5
            stats.setPassRate(Math.round(((double) passCount / totalExams) * 1000.0) / 10.0);
        } else {
            stats.setAvgScore(0);
            stats.setPassRate(0);
        }
        
        // Pending exams: Published AND targeted to this user AND no submission
        User user = userRepository.findByUsername(username).orElseThrow();
        List<Exam> publishedExams = examRepository.findByState("PUBLISHED");
        Set<Long> submittedExamIds = userSubmissions.stream()
                .filter(s -> "SUBMITTED".equals(s.getState()))
                .map(s -> s.getExam().getId())
                .collect(Collectors.toSet());
        
        long pending = publishedExams.stream()
                .filter(e -> !submittedExamIds.contains(e.getId()))
                .filter(e -> {
                    if ("ALL".equals(e.getTargetAudience())) return true;
                    if (e.getTargetIds() == null) return false;
                    String[] ids = e.getTargetIds().split(",");
                    for (String id : ids) {
                        String trimmed = id.trim();
                        if (trimmed.equals(user.getUsername()) || trimmed.equals(String.valueOf(user.getId()))) return true;
                    }
                    return false;
                })
                .count();
        stats.setPendingExamsCount(pending);
        
        // Recent submissions
        List<Submission> recent = userSubmissions.stream()
                .sorted((a, b) -> b.getEndTime().compareTo(a.getEndTime()))
                .limit(5)
                .collect(Collectors.toList());
        stats.setRecentSubmissions(recent);
        
        return stats;
    }

    public com.exam.dto.TeacherStatsDTO getTeacherStats() {
        com.exam.dto.TeacherStatsDTO stats = new com.exam.dto.TeacherStatsDTO();
        stats.setTotalExams(examRepository.count());
        stats.setTotalSubmissions(submissionRepository.count());
        stats.setActiveExams(examRepository.findByState("PUBLISHED").size());
        
        stats.setTotalStudents(submissionRepository.countDistinctStudents());
        
        List<Submission> recent = submissionRepository.findTop5ByOrderByEndTimeDesc();
        stats.setRecentSubmissions(recent);
        
        return stats;
    }
}
