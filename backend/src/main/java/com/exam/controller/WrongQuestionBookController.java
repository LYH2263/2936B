package com.exam.controller;

import com.exam.dto.WrongQuestionBookDTO;
import com.exam.entity.Question;
import com.exam.repository.UserRepository;
import com.exam.service.WrongQuestionBookService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
    public List<Question> getPracticeQuestions(
            Principal principal,
            @RequestParam(defaultValue = "10") int count) {
        return wrongQuestionBookService.getPracticeQuestions(principal.getName(), count);
    }

    @PostMapping("/practice/submit")
    public void submitPracticeResult(
            Principal principal,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<Long, String> answers = (Map<Long, String>) body.get("answers");
        @SuppressWarnings("unchecked")
        Map<Long, Boolean> correctMap = (Map<Long, Boolean>) body.get("correctMap");
        wrongQuestionBookService.submitPracticeResult(principal.getName(), answers, correctMap);
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
