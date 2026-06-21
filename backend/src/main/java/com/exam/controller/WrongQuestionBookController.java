package com.exam.controller;

import com.exam.dto.WrongQuestionBookDTO;
import com.exam.repository.UserRepository;
import com.exam.service.WrongQuestionBookService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wrong-book")
public class WrongQuestionBookController {

    private final WrongQuestionBookService wrongQuestionBookService;
    private final UserRepository userRepository;

    public WrongQuestionBookController(WrongQuestionBookService wrongQuestionBookService,
                                        UserRepository userRepository) {
        this.wrongQuestionBookService = wrongQuestionBookService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Page<WrongQuestionBookDTO> getWrongQuestions(
            Principal principal,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Boolean mastered,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return wrongQuestionBookService.getWrongQuestions(
                principal.getName(), subject, knowledgePoint, difficulty, mastered, page, size);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(Principal principal) {
        return wrongQuestionBookService.getStats(principal.getName());
    }

    @GetMapping("/subjects")
    public List<String> getSubjects(Principal principal) {
        return wrongQuestionBookService.getSubjects(principal.getName());
    }

    @GetMapping("/knowledge-points")
    public List<String> getKnowledgePoints(Principal principal) {
        return wrongQuestionBookService.getKnowledgePoints(principal.getName());
    }

    @GetMapping("/practice")
    public List<WrongQuestionBookDTO> getPracticeQuestions(
            Principal principal,
            @RequestParam(defaultValue = "10") int count) {
        return wrongQuestionBookService.getPracticeQuestions(principal.getName(), count);
    }

    @PostMapping("/practice/submit")
    public void submitPracticeResult(
            Principal principal,
            @RequestBody Map<String, Object> body) {
        wrongQuestionBookService.submitPracticeResult(
                principal.getName(),
                parseStringMap(body.get("answers")),
                parseBooleanMap(body.get("correctMap")));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, String> parseStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(Long.valueOf(entry.getKey().toString()), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Boolean> parseBooleanMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<Long, Boolean> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(Long.valueOf(entry.getKey().toString()), Boolean.TRUE.equals(entry.getValue()));
        }
        return result;
    }

    @DeleteMapping("/{questionId}")
    public void removeFromWrongBook(
            Principal principal,
            @PathVariable Long questionId) {
        Long studentId = userRepository.findByUsername(principal.getName())
                .orElseThrow().getId();
        wrongQuestionBookService.removeFromWrongBook(studentId, questionId);
    }

    @PutMapping("/{questionId}/mastered")
    public void markAsMastered(
            Principal principal,
            @PathVariable Long questionId) {
        Long studentId = userRepository.findByUsername(principal.getName())
                .orElseThrow().getId();
        wrongQuestionBookService.markAsMastered(studentId, questionId);
    }
}
