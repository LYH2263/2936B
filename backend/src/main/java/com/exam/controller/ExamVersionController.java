package com.exam.controller;

import com.exam.dto.ExamDiffResultDTO;
import com.exam.entity.Exam;
import com.exam.entity.ExamVersion;
import com.exam.service.ExamVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams/{examId}/versions")
@PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
public class ExamVersionController {

    private final ExamVersionService examVersionService;

    public ExamVersionController(ExamVersionService examVersionService) {
        this.examVersionService = examVersionService;
    }

    @GetMapping
    public List<ExamVersion> getVersions(@PathVariable Long examId) {
        return examVersionService.getVersions(examId);
    }

    @GetMapping("/diff")
    public ExamDiffResultDTO diffVersions(@PathVariable Long examId,
                                           @RequestParam Integer left,
                                           @RequestParam Integer right) {
        return examVersionService.diffVersions(examId, left, right);
    }

    @PostMapping("/rollback/{versionNumber}")
    public ResponseEntity<Exam> rollbackToVersion(@PathVariable Long examId,
                                                    @PathVariable Integer versionNumber,
                                                    Principal principal) {
        Exam exam = examVersionService.rollbackToVersion(examId, versionNumber, principal.getName());
        return ResponseEntity.ok(exam);
    }

    @GetMapping("/{versionNumber}")
    public ExamVersion getVersion(@PathVariable Long examId,
                                   @PathVariable Integer versionNumber) {
        return examVersionService.getVersion(examId, versionNumber);
    }

    @PostMapping
    public ResponseEntity<ExamVersion> createVersion(@PathVariable Long examId,
                                                      @RequestBody(required = false) Map<String, String> body,
                                                      Principal principal) {
        String description = (body != null && body.containsKey("description")) ? body.get("description") : null;
        ExamVersion version = examVersionService.createVersion(examId, principal.getName(), description);
        return ResponseEntity.ok(version);
    }
}
