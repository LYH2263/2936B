package com.exam.controller;

import com.exam.service.ExamQnaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams/qna")
public class ExamQnaController {

    private final ExamQnaService qnaService;

    public ExamQnaController(ExamQnaService qnaService) {
        this.qnaService = qnaService;
    }

    @GetMapping("/exam/{examId}")
    public ResponseEntity<List<Map<String, Object>>> getThreadsForExam(
            @PathVariable Long examId,
            Principal principal) {
        return ResponseEntity.ok(qnaService.getThreadsForExam(examId, principal));
    }

    @GetMapping("/thread/{threadId}")
    public ResponseEntity<Map<String, Object>> getThreadDetail(
            @PathVariable Long threadId,
            Principal principal) {
        return ResponseEntity.ok(qnaService.getThreadDetail(threadId, principal));
    }

    @PostMapping("/exam/{examId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> createThread(
            @PathVariable Long examId,
            @RequestBody Map<String, String> body,
            Principal principal) {
        String title = body.get("title");
        String content = body.get("content");
        return ResponseEntity.ok(qnaService.createThread(examId, title, content, principal));
    }

    @PostMapping("/thread/{threadId}/message")
    public ResponseEntity<Map<String, Object>> addMessage(
            @PathVariable Long threadId,
            @RequestBody Map<String, String> body,
            Principal principal) {
        String content = body.get("content");
        return ResponseEntity.ok(qnaService.addMessage(threadId, content, principal));
    }

    @PostMapping("/thread/{threadId}/faq")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> markAsFaq(
            @PathVariable Long threadId,
            @RequestBody(required = false) Map<String, Object> body,
            Principal principal) {
        boolean isFaq = true;
        if (body != null && body.containsKey("isFaq")) {
            Object v = body.get("isFaq");
            if (v instanceof Boolean) {
                isFaq = (Boolean) v;
            } else if (v instanceof String) {
                isFaq = Boolean.parseBoolean((String) v);
            }
        }
        return ResponseEntity.ok(qnaService.markAsFaq(threadId, isFaq, principal));
    }

    @PostMapping("/thread/{threadId}/pin")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> togglePin(
            @PathVariable Long threadId,
            Principal principal) {
        return ResponseEntity.ok(qnaService.togglePin(threadId, principal));
    }

    @GetMapping("/unanswered-count")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getUnansweredCount(Principal principal) {
        Map<String, Object> result = new HashMap<>();
        result.put("count", qnaService.getUnansweredCount(principal));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/unanswered")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getUnansweredThreads(Principal principal) {
        return ResponseEntity.ok(qnaService.getUnansweredThreads(principal));
    }
}
