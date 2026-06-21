package com.exam.controller;

import com.exam.entity.Exam;
import com.exam.entity.ExamTemplate;
import com.exam.entity.ExamTemplateQuestion;
import com.exam.service.ExamTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class ExamTemplateController {

    private final ExamTemplateService templateService;

    public ExamTemplateController(ExamTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<ExamTemplate> getVisibleTemplates(Principal principal) {
        return templateService.getVisibleTemplates(principal.getName());
    }

    @GetMapping("/search")
    public List<ExamTemplate> searchTemplates(
            @RequestParam(required = false) String course,
            @RequestParam(required = false) String keyword,
            Principal principal) {
        return templateService.searchTemplates(principal.getName(), course, keyword);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public List<ExamTemplate> getMyTemplates(Principal principal) {
        return templateService.getMyTemplates(principal.getName());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ExamTemplate> getPendingTemplates() {
        return templateService.getPendingTemplates();
    }

    @GetMapping("/{id}")
    public ExamTemplate getTemplate(@PathVariable Long id) {
        return templateService.getTemplateById(id);
    }

    @GetMapping("/{id}/questions")
    public List<ExamTemplateQuestion> getTemplateQuestions(@PathVariable Long id) {
        return templateService.getTemplateQuestions(id);
    }

    @PostMapping("/{examId}/save")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ExamTemplate saveAsTemplate(@PathVariable Long examId, @RequestBody Map<String, String> body, Principal principal) {
        String name = body.get("name");
        String description = body.get("description");
        String visibility = body.getOrDefault("visibility", "PRIVATE");
        String tags = body.get("tags");
        return templateService.saveAsTemplate(examId, name, description, visibility, tags, principal.getName());
    }

    @PostMapping("/{templateId}/create-exam")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public Exam createExamFromTemplate(@PathVariable Long templateId, @RequestBody Map<String, String> body, Principal principal) {
        String title = body.get("title");
        return templateService.createExamFromTemplate(templateId, title, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ExamTemplate updateTemplate(@PathVariable Long id, @RequestBody Map<String, String> body, Principal principal) {
        return templateService.updateTemplate(id, body.get("name"), body.get("description"),
                body.get("visibility"), body.get("tags"), principal.getName());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id, Principal principal) {
        templateService.deleteTemplate(id, principal.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamTemplate reviewTemplate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reviewStatus = body.get("reviewStatus");
        return templateService.reviewTemplate(id, reviewStatus);
    }
}
