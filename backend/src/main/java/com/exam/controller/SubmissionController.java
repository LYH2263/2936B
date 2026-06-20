package com.exam.controller;

import com.exam.entity.Submission;
import com.exam.service.SubmissionService;
import com.exam.dto.SubmissionGradeDTO;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/{examId}/start")
    public Submission startExam(@PathVariable Long examId, Principal principal) {
        return submissionService.startExam(examId, principal.getName());
    }

    @PostMapping("/{examId}")
    public Submission submitExam(@PathVariable Long examId, @RequestBody Map<Long, String> answers, Principal principal) {
        return submissionService.submitExam(examId, answers, principal.getName());
    }

    @GetMapping("/my")
    public List<Submission> getMySubmissions(Principal principal) {
        return submissionService.getStudentSubmissions(principal.getName());
    }
    
    @GetMapping("/exam/{examId}")
    public List<Submission> getExamSubmissions(@PathVariable Long examId) {
        return submissionService.getExamSubmissions(examId);
    }

    @GetMapping("/{id}")
    public Submission getSubmission(@PathVariable Long id) {
        return submissionService.getSubmissionById(id);
    }

    @PostMapping("/{id}/grade")
    public void gradeSubmission(@PathVariable Long id, @RequestBody Map<Long, SubmissionGradeDTO> grades) {
        submissionService.gradeSubmission(id, grades);
    }

    @GetMapping("/stats")
    public com.exam.dto.StudentStatsDTO getMyStats(Principal principal) {
        return submissionService.getStudentStats(principal.getName());
    }

    @GetMapping("/teacher-stats")
    public com.exam.dto.TeacherStatsDTO getTeacherStats() {
        return submissionService.getTeacherStats();
    }
}
