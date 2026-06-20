package com.exam.service;

import com.exam.dto.WrongQuestionBookDTO;
import com.exam.entity.*;
import com.exam.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WrongQuestionBookService {

    private final WrongQuestionBookRepository wrongQuestionBookRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamQuestionRepository examQuestionRepository;

    public WrongQuestionBookService(WrongQuestionBookRepository wrongQuestionBookRepository,
                                    UserRepository userRepository,
                                    QuestionRepository questionRepository,
                                    SubmissionRepository submissionRepository,
                                    ExamQuestionRepository examQuestionRepository) {
        this.wrongQuestionBookRepository = wrongQuestionBookRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.examQuestionRepository = examQuestionRepository;
    }

    @Transactional
    public void addWrongQuestion(Long studentId, Long questionId, Long submissionId,
                                  String studentAnswer, Integer scoreGot, Integer fullScore,
                                  String wrongReason) {
        Optional<WrongQuestionBook> existing = wrongQuestionBookRepository.findByStudentIdAndQuestionId(studentId, questionId);
        
        if (existing.isPresent()) {
            WrongQuestionBook wqb = existing.get();
            wqb.setWrongCount(wqb.getWrongCount() + 1);
            wqb.setLastWrongAt(LocalDateTime.now());
            wqb.setMastered(false);
            wqb.setStudentAnswer(studentAnswer);
            wqb.setScoreGot(scoreGot);
            wqb.setFullScore(fullScore);
            if (wrongReason != null) {
                wqb.setWrongReason(wrongReason);
            }
            if (submissionId != null) {
                Submission submission = submissionRepository.findById(submissionId).orElse(null);
                if (submission != null) {
                    wqb.setSubmission(submission);
                }
            }
            wrongQuestionBookRepository.save(wqb);
        } else {
            User student = userRepository.findById(studentId).orElseThrow();
            Question question = questionRepository.findById(questionId).orElseThrow();
            
            WrongQuestionBook wqb = new WrongQuestionBook();
            wqb.setStudent(student);
            wqb.setQuestion(question);
            wqb.setStudentAnswer(studentAnswer);
            wqb.setScoreGot(scoreGot);
            wqb.setFullScore(fullScore);
            wqb.setWrongReason(wrongReason);
            wqb.setMastered(false);
            wqb.setWrongCount(1);
            
            if (submissionId != null) {
                Submission submission = submissionRepository.findById(submissionId).orElse(null);
                if (submission != null) {
                    wqb.setSubmission(submission);
                }
            }
            
            wrongQuestionBookRepository.save(wqb);
        }
    }

    @Transactional
    public void processSubmissionWrongQuestions(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        
        Long studentId = submission.getStudent().getId();
        Long examId = submission.getExam().getId();
        
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySequenceAsc(examId);
        Map<Long, Integer> questionFullScoreMap = examQuestions.stream()
                .collect(Collectors.toMap(eq -> eq.getQuestion().getId(), ExamQuestion::getScore));
        
        for (SubmissionAnswer answer : submission.getAnswers()) {
            Question question = answer.getQuestion();
            Integer fullScore = questionFullScoreMap.getOrDefault(question.getId(), question.getDefaultScore());
            Integer scoreGot = answer.getScore() != null ? answer.getScore() : 0;
            
            boolean isWrong = false;
            String wrongReason = null;
            
            if ("SINGLE".equals(question.getType()) || "MULTI".equals(question.getType()) || "JUDGE".equals(question.getType())) {
                if (scoreGot < fullScore) {
                    isWrong = true;
                    wrongReason = "客观题答错或未得满分";
                }
            } else if ("SHORT".equals(question.getType())) {
                if (scoreGot < fullScore * 0.6) {
                    isWrong = true;
                    wrongReason = "主观题得分低于60%";
                }
            }
            
            if (isWrong) {
                addWrongQuestion(studentId, question.getId(), submissionId,
                        answer.getStudentAnswer(), scoreGot, fullScore, wrongReason);
            }
        }
    }

    @Transactional
    public void removeFromWrongBook(Long studentId, Long questionId) {
        wrongQuestionBookRepository.deleteByStudentIdAndQuestionId(studentId, questionId);
    }

    @Transactional
    public void markAsMastered(Long studentId, Long questionId) {
        WrongQuestionBook wqb = wrongQuestionBookRepository.findByStudentIdAndQuestionId(studentId, questionId)
                .orElseThrow(() -> new RuntimeException("Wrong question record not found"));
        wqb.setMastered(true);
        wrongQuestionBookRepository.save(wqb);
    }

    @Transactional(readOnly = true)
    public Page<WrongQuestionBookDTO> getWrongQuestions(String username, String subject, 
                                                      String knowledgePoint, Integer difficulty,
                                                      Boolean mastered, int page, int size) {
        User student = userRepository.findByUsername(username).orElseThrow();
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastWrongAt"));
        
        Page<WrongQuestionBook> pageResult;
        if (subject != null || knowledgePoint != null || difficulty != null || mastered != null) {
            pageResult = wrongQuestionBookRepository.findByStudentIdWithFilters(
                    student.getId(), subject, knowledgePoint, difficulty, mastered, pageable);
        } else {
            pageResult = wrongQuestionBookRepository.findByStudentId(student.getId(), pageable);
        }
        
        return pageResult.map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public List<Question> getPracticeQuestions(String username, int count) {
        User student = userRepository.findByUsername(username).orElseThrow();
        
        Pageable pageable = PageRequest.of(0, count * 3);
        List<WrongQuestionBook> wrongQuestions = wrongQuestionBookRepository.findRandomWrongQuestions(
                student.getId(), pageable);
        
        List<Question> questions = wrongQuestions.stream()
                .map(WrongQuestionBook::getQuestion)
                .collect(Collectors.toList());
        
        Collections.shuffle(questions);
        
        return questions.stream().limit(count).collect(Collectors.toList());
    }

    public Map<String, Object> getStats(String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        Long studentId = student.getId();
        
        Map<String, Object> stats = new HashMap<>();
        
        long totalWrong = wrongQuestionBookRepository.countByStudentIdAndMasteredFalse(studentId);
        long mastered = wrongQuestionBookRepository.countByStudentIdAndMasteredTrue(studentId);
        
        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
        long weekNew = wrongQuestionBookRepository.countAddedSince(studentId, weekAgo);
        long weekMastered = wrongQuestionBookRepository.countMasteredSince(studentId, weekAgo);
        
        stats.put("totalWrong", totalWrong);
        stats.put("mastered", mastered);
        stats.put("weekNew", weekNew);
        stats.put("weekMastered", weekMastered);
        
        return stats;
    }

    public List<String> getSubjects(String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        return wrongQuestionBookRepository.findDistinctSubjectsByStudentId(student.getId());
    }

    public List<String> getKnowledgePoints(String username) {
        User student = userRepository.findByUsername(username).orElseThrow();
        return wrongQuestionBookRepository.findDistinctKnowledgePointsByStudentId(student.getId());
    }

    @Transactional
    public void submitPracticeResult(String username, Map<Long, String> answers,
                                      Map<Long, Boolean> correctMap) {
        User student = userRepository.findByUsername(username).orElseThrow();
        Long studentId = student.getId();
        
        for (Map.Entry<Long, Boolean> entry : correctMap.entrySet()) {
            Long questionId = entry.getKey();
            boolean isCorrect = entry.getValue();
            
            if (isCorrect) {
                Optional<WrongQuestionBook> existing = wrongQuestionBookRepository
                        .findByStudentIdAndQuestionId(studentId, questionId);
                if (existing.isPresent()) {
                    WrongQuestionBook wqb = existing.get();
                    wqb.setMastered(true);
                    wrongQuestionBookRepository.save(wqb);
                }
            } else {
                Optional<WrongQuestionBook> existing = wrongQuestionBookRepository
                        .findByStudentIdAndQuestionId(studentId, questionId);
                if (existing.isPresent()) {
                    WrongQuestionBook wqb = existing.get();
                    wqb.setWrongCount(wqb.getWrongCount() + 1);
                    wqb.setLastWrongAt(LocalDateTime.now());
                    wqb.setMastered(false);
                    wrongQuestionBookRepository.save(wqb);
                }
            }
        }
    }

    private WrongQuestionBookDTO convertToDTO(WrongQuestionBook wqb) {
        WrongQuestionBookDTO dto = new WrongQuestionBookDTO();
        dto.setId(wqb.getId());
        dto.setStudentAnswer(wqb.getStudentAnswer());
        dto.setScoreGot(wqb.getScoreGot());
        dto.setFullScore(wqb.getFullScore());
        dto.setWrongReason(wqb.getWrongReason());
        dto.setMastered(wqb.getMastered());
        dto.setWrongCount(wqb.getWrongCount());
        dto.setAddedAt(wqb.getAddedAt());
        dto.setLastWrongAt(wqb.getLastWrongAt());
        
        if (wqb.getQuestion() != null) {
            Question q = wqb.getQuestion();
            dto.setQuestionId(q.getId());
            dto.setContent(q.getContent());
            dto.setType(q.getType());
            dto.setOptions(q.getOptions());
            dto.setAnswer(q.getAnswer());
            dto.setAnalysis(q.getAnalysis());
            dto.setSubject(q.getSubject());
            dto.setKnowledgePoint(q.getKnowledgePoint());
            dto.setDifficulty(q.getDifficulty());
            dto.setDefaultScore(q.getDefaultScore());
        }
        
        if (wqb.getSubmission() != null) {
            dto.setSubmissionId(wqb.getSubmission().getId());
        }
        
        return dto;
    }
}
