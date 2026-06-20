package com.exam.service;

import com.exam.dto.AnswerSnapshotDTO;
import com.exam.dto.ReplayDataDTO;
import com.exam.entity.*;
import com.exam.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnswerSnapshotService {

    private final AnswerSnapshotRepository snapshotRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionService submissionService;
    private final ExamQuestionRepository examQuestionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final int FULL_SNAPSHOT_INTERVAL = 10;
    private static final int MAX_SNAPSHOTS_PER_EXAM = 500;

    public AnswerSnapshotService(AnswerSnapshotRepository snapshotRepository,
                                  SubmissionRepository submissionRepository,
                                  SubmissionService submissionService,
                                  ExamQuestionRepository examQuestionRepository,
                                  UserRepository userRepository,
                                  ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.submissionRepository = submissionRepository;
        this.submissionService = submissionService;
        this.examQuestionRepository = examQuestionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnswerSnapshot createSnapshot(AnswerSnapshotDTO dto, String username) {
        Submission submission = submissionRepository.findById(dto.getSubmissionId())
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        if (!submission.getStudent().getUsername().equals(username)) {
            throw new SecurityException("Unauthorized to create snapshot for this submission");
        }

        if ("SUBMITTED".equals(submission.getState())) {
            throw new IllegalStateException("Cannot create snapshot for submitted exam");
        }

        long snapshotCount = snapshotRepository.countBySubmissionId(dto.getSubmissionId());
        if (snapshotCount >= MAX_SNAPSHOTS_PER_EXAM) {
            return null;
        }

        boolean isFullSnapshot = (snapshotCount % FULL_SNAPSHOT_INTERVAL == 0);

        Map<Long, String> delta;
        if (isFullSnapshot) {
            delta = dto.getAnswers() != null ? new HashMap<>(dto.getAnswers()) : new HashMap<>();
        } else {
            Optional<AnswerSnapshot> lastSnapshotOpt = snapshotRepository.findLatestBySubmissionId(dto.getSubmissionId());
            Map<Long, String> lastAnswers = lastSnapshotOpt
                    .map(s -> parseAnswers(s.getAnswersDelta(), s.getIsFullSnapshot(), dto.getSubmissionId()))
                    .orElse(new HashMap<>());
            delta = computeDelta(lastAnswers, dto.getAnswers());
        }

        if (delta.isEmpty() && !isFullSnapshot) {
            return null;
        }

        AnswerSnapshot snapshot = new AnswerSnapshot();
        snapshot.setSubmission(submission);
        snapshot.setTimestamp(LocalDateTime.now());
        snapshot.setElapsedSeconds(dto.getElapsedSeconds());
        snapshot.setCurrentQuestionIndex(dto.getCurrentQuestionIndex());
        snapshot.setTimeLeft(dto.getTimeLeft());
        snapshot.setIsFullSnapshot(isFullSnapshot);

        try {
            String deltaJson = objectMapper.writeValueAsString(delta);
            snapshot.setAnswersDelta(deltaJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize answers delta", e);
        }

        return snapshotRepository.save(snapshot);
    }

    private Map<Long, String> computeDelta(Map<Long, String> previous, Map<Long, String> current) {
        Map<Long, String> delta = new HashMap<>();
        if (current == null) current = new HashMap<>();

        for (Map.Entry<Long, String> entry : current.entrySet()) {
            Long key = entry.getKey();
            String value = entry.getValue();
            String prevValue = previous.get(key);
            if (!Objects.equals(prevValue, value)) {
                delta.put(key, value);
            }
        }

        for (Long key : previous.keySet()) {
            if (!current.containsKey(key)) {
                delta.put(key, "");
            }
        }

        return delta;
    }

    private Map<Long, String> parseAnswers(String deltaJson, boolean isFullSnapshot, Long submissionId) {
        try {
            Map<Long, String> delta = objectMapper.readValue(deltaJson, new TypeReference<Map<Long, String>>() {});
            if (isFullSnapshot) {
                return new HashMap<>(delta);
            }
            return delta;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Transactional(readOnly = true)
    public ReplayDataDTO getReplayData(Long submissionId, String username) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isTeacher = "TEACHER".equals(currentUser.getRole()) || "ADMIN".equals(currentUser.getRole());
        boolean isOwner = submission.getStudent().getUsername().equals(username);

        if (!isTeacher && !isOwner) {
            throw new SecurityException("Unauthorized to view this replay");
        }

        Exam exam = submission.getExam();
        if (isOwner) {
            if (!Boolean.TRUE.equals(exam.getAllowViewAnalysis())) {
                throw new SecurityException("Analysis view is not allowed for this exam");
            }
            if (!"ENDED".equals(exam.getState()) && !"PUBLISHED".equals(exam.getState())) {
                throw new SecurityException("Exam results are not published yet");
            }
        }

        ReplayDataDTO result = new ReplayDataDTO();
        result.setSubmissionId(submissionId);
        result.setExamId(exam.getId());
        result.setExamTitle(exam.getTitle());
        result.setStudentName(submission.getStudent().getFullName());
        result.setTotalDuration(exam.getDuration() * 60);
        result.setCanViewAnalysis(exam.getAllowViewAnalysis());
        result.setIsTeacherView(isTeacher);

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySequenceAsc(exam.getId());
        List<ReplayDataDTO.ExamQuestionDTO> questionDTOs = examQuestions.stream().map(eq -> {
            ReplayDataDTO.ExamQuestionDTO qdto = new ReplayDataDTO.ExamQuestionDTO();
            qdto.setId(eq.getId());
            qdto.setQuestionId(eq.getQuestion().getId());
            qdto.setType(eq.getQuestion().getType());
            qdto.setContent(eq.getQuestion().getContent());
            qdto.setOptions(eq.getQuestion().getOptions());
            qdto.setScore(eq.getScore());
            qdto.setSequence(eq.getSequence());
            return qdto;
        }).collect(Collectors.toList());
        result.setQuestions(questionDTOs);

        List<AnswerSnapshot> snapshots = snapshotRepository.findBySubmissionIdOrderByTimestampAsc(submissionId);
        result.setTimeline(buildTimeline(snapshots));

        return result;
    }

    private List<ReplayDataDTO.ReplaySnapshotDTO> buildTimeline(List<AnswerSnapshot> snapshots) {
        List<ReplayDataDTO.ReplaySnapshotDTO> timeline = new ArrayList<>();
        Map<Long, String> accumulatedAnswers = new HashMap<>();

        for (AnswerSnapshot snapshot : snapshots) {
            try {
                Map<Long, String> delta = objectMapper.readValue(
                        snapshot.getAnswersDelta(),
                        new TypeReference<Map<Long, String>>() {});

                if (snapshot.getIsFullSnapshot()) {
                    accumulatedAnswers = new HashMap<>(delta);
                } else {
                    for (Map.Entry<Long, String> entry : delta.entrySet()) {
                        Long key = entry.getKey();
                        String value = entry.getValue();
                        if (value == null || value.isEmpty()) {
                            accumulatedAnswers.remove(key);
                        } else {
                            accumulatedAnswers.put(key, value);
                        }
                    }
                }

                ReplayDataDTO.ReplaySnapshotDTO dto = new ReplayDataDTO.ReplaySnapshotDTO();
                dto.setId(snapshot.getId());
                dto.setElapsedSeconds(snapshot.getElapsedSeconds());
                dto.setCurrentQuestionIndex(snapshot.getCurrentQuestionIndex());
                dto.setTimeLeft(snapshot.getTimeLeft());
                dto.setAnswers(new HashMap<>(accumulatedAnswers));

                timeline.add(dto);
            } catch (Exception e) {
                // Skip corrupted snapshot
            }
        }

        return timeline;
    }

    @Transactional(readOnly = true)
    public boolean canViewReplay(Long submissionId, String username) {
        try {
            getReplayData(submissionId, username);
            return true;
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
