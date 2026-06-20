package com.exam.service;

import com.exam.entity.Exam;
import com.exam.entity.ExamQuestion;
import com.exam.entity.ExamTemplate;
import com.exam.entity.ExamTemplateQuestion;
import com.exam.entity.Question;
import com.exam.entity.User;
import com.exam.repository.ExamQuestionRepository;
import com.exam.repository.ExamRepository;
import com.exam.repository.ExamTemplateQuestionRepository;
import com.exam.repository.ExamTemplateRepository;
import com.exam.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamTemplateService {

    private final ExamTemplateRepository templateRepository;
    private final ExamTemplateQuestionRepository templateQuestionRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final UserRepository userRepository;

    public ExamTemplateService(ExamTemplateRepository templateRepository,
                               ExamTemplateQuestionRepository templateQuestionRepository,
                               ExamRepository examRepository,
                               ExamQuestionRepository examQuestionRepository,
                               UserRepository userRepository) {
        this.templateRepository = templateRepository;
        this.templateQuestionRepository = templateQuestionRepository;
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.userRepository = userRepository;
    }

    public List<ExamTemplate> getVisibleTemplates(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return templateRepository.findVisibleTemplates(user.getId());
    }

    public List<ExamTemplate> searchTemplates(String username, String course, String keyword) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return templateRepository.searchTemplates(user.getId(), course, keyword);
    }

    public ExamTemplate getTemplateById(Long id) {
        return templateRepository.findById(id).orElseThrow(() -> new RuntimeException("Template not found"));
    }

    public List<ExamTemplateQuestion> getTemplateQuestions(Long templateId) {
        return templateQuestionRepository.findByTemplateIdOrderBySequenceAsc(templateId);
    }

    @Transactional
    public ExamTemplate saveAsTemplate(Long examId, String name, String description,
                                        String visibility, String tags, String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        Exam exam = examRepository.findById(examId).orElseThrow(() -> new RuntimeException("Exam not found"));

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySequenceAsc(examId);
        if (examQuestions.isEmpty()) {
            throw new RuntimeException("Cannot save empty exam as template");
        }

        ExamTemplate template = new ExamTemplate();
        template.setName(name != null ? name : exam.getTitle() + "（模板）");
        template.setDescription(description != null ? description : exam.getDescription());
        template.setCourse(exam.getCourse());
        template.setVisibility(visibility != null ? visibility : "PRIVATE");
        template.setTags(tags);
        template.setDuration(exam.getDuration());
        template.setCoverUrl(exam.getCoverUrl());
        template.setCreator(user);

        if ("PUBLIC".equals(visibility)) {
            template.setReviewStatus("PENDING");
        } else {
            template.setReviewStatus("APPROVED");
        }

        template = templateRepository.save(template);

        for (ExamQuestion eq : examQuestions) {
            ExamTemplateQuestion tq = new ExamTemplateQuestion();
            tq.setTemplate(template);
            tq.setQuestion(eq.getQuestion());
            tq.setScore(eq.getScore());
            tq.setSequence(eq.getSequence());
            templateQuestionRepository.save(tq);
        }

        return template;
    }

    @Transactional
    public Exam createExamFromTemplate(Long templateId, String title, String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        ExamTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        boolean canAccess = template.getCreator().getId().equals(user.getId())
                || ("PUBLIC".equals(template.getVisibility()) && "APPROVED".equals(template.getReviewStatus()));
        if (!canAccess) {
            throw new RuntimeException("No access to this template");
        }

        List<ExamTemplateQuestion> templateQuestions = templateQuestionRepository
                .findByTemplateIdOrderBySequenceAsc(templateId);
        if (templateQuestions.isEmpty()) {
            throw new RuntimeException("Template has no questions");
        }

        Exam exam = new Exam();
        exam.setTitle(title != null ? title : template.getName());
        exam.setDescription(template.getDescription());
        exam.setCourse(template.getCourse());
        exam.setDuration(template.getDuration());
        exam.setCoverUrl(template.getCoverUrl());
        exam.setState("DRAFT");
        exam.setCreator(user);
        exam = examRepository.save(exam);

        for (ExamTemplateQuestion tq : templateQuestions) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExam(exam);
            eq.setQuestion(tq.getQuestion());
            eq.setScore(tq.getScore());
            eq.setSequence(tq.getSequence());
            examQuestionRepository.save(eq);
        }

        return exam;
    }

    @Transactional
    public void deleteTemplate(Long templateId, String username) {
        ExamTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        User user = userRepository.findByUsername(username).orElseThrow();

        boolean isCreator = template.getCreator().getId().equals(user.getId());
        boolean isAdmin = "ADMIN".equals(user.getRole());
        if (!isCreator && !isAdmin) {
            throw new RuntimeException("No permission to delete this template");
        }

        templateQuestionRepository.deleteByTemplateId(templateId);
        templateRepository.delete(template);
    }

    @Transactional
    public ExamTemplate updateTemplate(Long templateId, String name, String description,
                                        String visibility, String tags, String username) {
        ExamTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        User user = userRepository.findByUsername(username).orElseThrow();

        if (!template.getCreator().getId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new RuntimeException("No permission to update this template");
        }

        if (name != null) template.setName(name);
        if (description != null) template.setDescription(description);
        if (tags != null) template.setTags(tags);

        if (visibility != null && !visibility.equals(template.getVisibility())) {
            template.setVisibility(visibility);
            if ("PUBLIC".equals(visibility)) {
                template.setReviewStatus("PENDING");
            } else {
                template.setReviewStatus("APPROVED");
            }
        }

        return templateRepository.save(template);
    }

    @Transactional
    public ExamTemplate reviewTemplate(Long templateId, String reviewStatus) {
        ExamTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        template.setReviewStatus(reviewStatus);
        return templateRepository.save(template);
    }

    public List<ExamTemplate> getPendingTemplates() {
        return templateRepository.findByReviewStatus("PENDING");
    }

    public List<ExamTemplate> getMyTemplates(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return templateRepository.findByCreatorIdOrderByCreatedAtDesc(user.getId());
    }
}
