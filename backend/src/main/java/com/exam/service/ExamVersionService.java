package com.exam.service;

import com.exam.dto.ExamDiffItemDTO;
import com.exam.dto.ExamDiffResultDTO;
import com.exam.dto.ExamVersionQuestionDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamQuestion;
import com.exam.entity.ExamVersion;
import com.exam.entity.Question;
import com.exam.entity.User;
import com.exam.repository.ExamQuestionRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamVersionRepository;
import com.exam.repository.QuestionRepository;
import com.exam.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamVersionService {

    private static final int MAX_VERSIONS = 20;

    private final ExamVersionRepository examVersionRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ExamVersionService(ExamVersionRepository examVersionRepository,
                              ExamRepository examRepository,
                              ExamQuestionRepository examQuestionRepository,
                              QuestionRepository questionRepository,
                              UserRepository userRepository,
                              ObjectMapper objectMapper) {
        this.examVersionRepository = examVersionRepository;
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExamVersion createVersion(Long examId, String username, String description) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        User user = userRepository.findByUsername(username).orElse(null);

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySequenceAsc(examId);
        List<ExamVersionQuestionDTO> snapshot = examQuestions.stream()
                .map(eq -> {
                    ExamVersionQuestionDTO dto = new ExamVersionQuestionDTO();
                    dto.setQuestionId(eq.getQuestion().getId());
                    dto.setScore(eq.getScore());
                    dto.setSequence(eq.getSequence());
                    return dto;
                })
                .collect(Collectors.toList());

        String snapshotData;
        String contentHash;
        try {
            snapshotData = objectMapper.writeValueAsString(snapshot);
            contentHash = computeHash(snapshotData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize snapshot", e);
        }

        ExamVersion latest = examVersionRepository.findByExamIdOrderByVersionNumberDesc(examId)
                .stream().findFirst().orElse(null);
        if (latest != null && latest.getContentHash().equals(contentHash)) {
            return latest;
        }

        Integer maxVersion = examVersionRepository.findMaxVersionNumberByExamId(examId);
        int nextVersion = (maxVersion == null) ? 1 : maxVersion + 1;

        ExamVersion version = new ExamVersion();
        version.setExam(exam);
        version.setVersionNumber(nextVersion);
        version.setSnapshotData(snapshotData);
        version.setContentHash(contentHash);
        version.setDescription(description);
        version.setCreatedBy(user);

        ExamVersion saved = examVersionRepository.save(version);
        cleanupOldVersions(examId);
        return saved;
    }

    private void cleanupOldVersions(Long examId) {
        long count = examVersionRepository.countByExamId(examId);
        if (count > MAX_VERSIONS) {
            List<ExamVersion> allVersions = examVersionRepository.findByExamIdOrderByCreatedAtAsc(examId);
            int toRemove = (int) (count - MAX_VERSIONS);
            List<ExamVersion> oldVersions = allVersions.subList(0, toRemove);
            examVersionRepository.deleteAll(oldVersions);
        }
    }

    public List<ExamVersion> getVersions(Long examId) {
        return examVersionRepository.findByExamIdOrderByVersionNumberDesc(examId);
    }

    public ExamVersion getVersion(Long examId, Integer versionNumber) {
        return examVersionRepository.findByExamIdAndVersionNumber(examId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found"));
    }

    public ExamDiffResultDTO diffVersions(Long examId, Integer leftVersionNum, Integer rightVersionNum) {
        ExamVersion leftVersion = getVersion(examId, leftVersionNum);
        ExamVersion rightVersion = getVersion(examId, rightVersionNum);

        List<ExamVersionQuestionDTO> leftQuestions = parseSnapshot(leftVersion.getSnapshotData());
        List<ExamVersionQuestionDTO> rightQuestions = parseSnapshot(rightVersion.getSnapshotData());

        enrichWithQuestionDetails(leftQuestions);
        enrichWithQuestionDetails(rightQuestions);

        List<ExamDiffItemDTO> diffItems = computeDiff(leftQuestions, rightQuestions);

        ExamDiffResultDTO result = new ExamDiffResultDTO();
        result.setLeftVersionId(leftVersion.getId());
        result.setLeftVersionNumber(leftVersion.getVersionNumber());
        result.setRightVersionId(rightVersion.getId());
        result.setRightVersionNumber(rightVersion.getVersionNumber());
        result.setDiffItems(diffItems);

        int added = 0, removed = 0, scoreChanged = 0, seqChanged = 0;
        for (ExamDiffItemDTO item : diffItems) {
            switch (item.getChangeType()) {
                case "ADDED": added++; break;
                case "REMOVED": removed++; break;
                case "SCORE_CHANGED": scoreChanged++; break;
                case "SEQUENCE_CHANGED": seqChanged++; break;
            }
        }
        result.setAddedCount(added);
        result.setRemovedCount(removed);
        result.setScoreChangedCount(scoreChanged);
        result.setSequenceChangedCount(seqChanged);

        return result;
    }

    private List<ExamDiffItemDTO> computeDiff(List<ExamVersionQuestionDTO> left,
                                               List<ExamVersionQuestionDTO> right) {
        Map<Long, ExamVersionQuestionDTO> leftMap = new HashMap<>();
        Map<Long, Integer> leftSeqMap = new HashMap<>();
        for (int i = 0; i < left.size(); i++) {
            ExamVersionQuestionDTO q = left.get(i);
            leftMap.put(q.getQuestionId(), q);
            leftSeqMap.put(q.getQuestionId(), i);
        }

        Map<Long, ExamVersionQuestionDTO> rightMap = new HashMap<>();
        Map<Long, Integer> rightSeqMap = new HashMap<>();
        for (int i = 0; i < right.size(); i++) {
            ExamVersionQuestionDTO q = right.get(i);
            rightMap.put(q.getQuestionId(), q);
            rightSeqMap.put(q.getQuestionId(), i);
        }

        Set<Long> allIds = new HashSet<>();
        allIds.addAll(leftMap.keySet());
        allIds.addAll(rightMap.keySet());

        List<ExamDiffItemDTO> result = new ArrayList<>();

        for (Long id : allIds) {
            boolean inLeft = leftMap.containsKey(id);
            boolean inRight = rightMap.containsKey(id);

            if (!inLeft && inRight) {
                ExamDiffItemDTO item = new ExamDiffItemDTO();
                item.setChangeType("ADDED");
                item.setRightQuestion(rightMap.get(id));
                result.add(item);
            } else if (inLeft && !inRight) {
                ExamDiffItemDTO item = new ExamDiffItemDTO();
                item.setChangeType("REMOVED");
                item.setLeftQuestion(leftMap.get(id));
                result.add(item);
            } else {
                ExamVersionQuestionDTO lq = leftMap.get(id);
                ExamVersionQuestionDTO rq = rightMap.get(id);

                boolean scoreChanged = !Objects.equals(lq.getScore(), rq.getScore());
                boolean seqChanged = !Objects.equals(leftSeqMap.get(id), rightSeqMap.get(id));

                if (scoreChanged) {
                    ExamDiffItemDTO item = new ExamDiffItemDTO();
                    item.setChangeType("SCORE_CHANGED");
                    item.setLeftQuestion(lq);
                    item.setRightQuestion(rq);
                    item.setScoreChange((rq.getScore() != null ? rq.getScore() : 0)
                            - (lq.getScore() != null ? lq.getScore() : 0));
                    result.add(item);
                } else if (seqChanged) {
                    ExamDiffItemDTO item = new ExamDiffItemDTO();
                    item.setChangeType("SEQUENCE_CHANGED");
                    item.setLeftQuestion(lq);
                    item.setRightQuestion(rq);
                    item.setSequenceChange(rightSeqMap.get(id) - leftSeqMap.get(id));
                    result.add(item);
                }
            }
        }

        result.sort((a, b) -> {
            int seqA = a.getRightQuestion() != null ? a.getRightQuestion().getSequence()
                    : (a.getLeftQuestion() != null ? a.getLeftQuestion().getSequence() : 0);
            int seqB = b.getRightQuestion() != null ? b.getRightQuestion().getSequence()
                    : (b.getLeftQuestion() != null ? b.getLeftQuestion().getSequence() : 0);
            return Integer.compare(seqA, seqB);
        });

        return result;
    }

    @Transactional
    public Exam rollbackToVersion(Long examId, Integer versionNumber, String username) {
        ExamVersion version = getVersion(examId, versionNumber);
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        if (!"DRAFT".equals(exam.getState())) {
            throw new RuntimeException("只能回滚草稿状态的试卷");
        }

        List<ExamVersionQuestionDTO> snapshot = parseSnapshot(version.getSnapshotData());

        List<ExamQuestion> existingQuestions = examQuestionRepository.findByExamIdOrderBySequenceAsc(examId);
        examQuestionRepository.deleteAll(existingQuestions);

        for (ExamVersionQuestionDTO sq : snapshot) {
            Question question = questionRepository.findById(sq.getQuestionId())
                    .orElseThrow(() -> new RuntimeException("Question not found: " + sq.getQuestionId()));

            ExamQuestion eq = new ExamQuestion();
            eq.setExam(exam);
            eq.setQuestion(question);
            eq.setScore(sq.getScore());
            eq.setSequence(sq.getSequence());
            examQuestionRepository.save(eq);
        }

        createVersion(examId, username, "回滚到版本 v" + versionNumber);

        return exam;
    }

    private List<ExamVersionQuestionDTO> parseSnapshot(String snapshotData) {
        try {
            return objectMapper.readValue(snapshotData,
                    new TypeReference<List<ExamVersionQuestionDTO>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse snapshot data", e);
        }
    }

    private void enrichWithQuestionDetails(List<ExamVersionQuestionDTO> questions) {
        if (questions == null || questions.isEmpty()) return;

        Set<Long> ids = questions.stream()
                .map(ExamVersionQuestionDTO::getQuestionId)
                .collect(Collectors.toSet());

        List<Question> questionList = questionRepository.findAllById(ids);
        Map<Long, Question> questionMap = questionList.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        for (ExamVersionQuestionDTO dto : questions) {
            Question q = questionMap.get(dto.getQuestionId());
            if (q != null) {
                dto.setContent(q.getContent());
                dto.setType(q.getType());
                dto.setSubject(q.getSubject());
            }
        }
    }

    private String computeHash(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
