package com.exam.controller;

import com.exam.dto.*;
import com.exam.entity.Exam;
import com.exam.entity.Question;
import com.exam.service.GradingWorkbenchService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grading-workbench")
public class GradingWorkbenchController {

    private final GradingWorkbenchService gradingWorkbenchService;

    public GradingWorkbenchController(GradingWorkbenchService gradingWorkbenchService) {
        this.gradingWorkbenchService = gradingWorkbenchService;
    }

    @GetMapping("/exams")
    public List<Exam> getTeacherExams(Principal principal) {
        return gradingWorkbenchService.getTeacherExamsWithSubjective(principal.getName());
    }

    @GetMapping("/exam/{examId}/questions")
    public List<Map<String, Object>> getSubjectiveQuestions(@PathVariable Long examId) {
        return gradingWorkbenchService.getSubjectiveQuestionsByExam(examId);
    }

    @GetMapping("/queue")
    public List<GradingQueueItemDTO> getGradingQueue(
            @RequestParam Long examId,
            @RequestParam(required = false) Long questionId,
            @RequestParam(defaultValue = "10") int limit) {
        return gradingWorkbenchService.getGradingQueue(examId, questionId, limit);
    }

    @GetMapping("/question/{questionId}")
    public Question getQuestionDetail(@PathVariable Long questionId) {
        return gradingWorkbenchService.getQuestionDetail(questionId);
    }

    @PutMapping("/question/{questionId}/rubric")
    public Question updateRubric(
            @PathVariable Long questionId,
            @RequestBody Map<String, String> body) {
        return gradingWorkbenchService.updateRubric(questionId, body.get("rubric"));
    }

    @PostMapping("/batch-grade")
    public BatchGradeResultDTO batchGrade(
            @RequestBody List<BatchGradeItemDTO> items,
            Principal principal) {
        return gradingWorkbenchService.batchGrade(items, principal.getName());
    }

    @GetMapping("/stats")
    public GradingWorkbenchStatsDTO getStats(Principal principal) {
        return gradingWorkbenchService.getTeacherStats(principal.getName());
    }
}
