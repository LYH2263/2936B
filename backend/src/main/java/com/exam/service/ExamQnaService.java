package com.exam.service;

import com.exam.entity.*;
import com.exam.repository.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamQnaService {

    private final ExamQnaThreadRepository threadRepository;
    private final ExamQnaMessageRepository messageRepository;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private static final Safelist XSS_SAFELIST = Safelist.relaxed()
            .addTags("p", "br", "strong", "em", "u", "ol", "ul", "li", "blockquote", "code", "pre")
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "http", "https", "mailto");

    public ExamQnaService(ExamQnaThreadRepository threadRepository,
                           ExamQnaMessageRepository messageRepository,
                           ExamRepository examRepository,
                           UserRepository userRepository,
                           NotificationService notificationService) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    private String sanitizeHtml(String unsafe) {
        if (unsafe == null) return null;
        return Jsoup.clean(unsafe, XSS_SAFELIST);
    }

    private boolean isExamEnded(Exam exam) {
        if ("ENDED".equals(exam.getState())) return true;
        if (exam.getEndTime() != null) {
            return LocalDateTime.now().isAfter(exam.getEndTime());
        }
        return false;
    }

    private boolean canPostQuestion(Exam exam) {
        if ("DRAFT".equals(exam.getState())) return false;
        if (exam.getStartTime() != null && LocalDateTime.now().isBefore(exam.getStartTime())) {
            return true;
        }
        if (exam.getEndTime() != null && LocalDateTime.now().isBefore(exam.getEndTime())) {
            return true;
        }
        return "ENDED".equals(exam.getState());
    }

    @Transactional
    public Map<String, Object> createThread(Long examId, String title, String content, Principal principal) {
        User student = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!"STUDENT".equals(student.getRole())) {
            throw new RuntimeException("只有学生可以发起提问");
        }

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        if (!canPostQuestion(exam)) {
            throw new RuntimeException("当前考试状态不允许提问");
        }

        String safeTitle = sanitizeHtml(title);
        String safeContent = sanitizeHtml(content);
        if (safeTitle == null || safeTitle.trim().isEmpty()) {
            throw new RuntimeException("提问标题不能为空");
        }

        ExamQnaThread thread = new ExamQnaThread();
        thread.setExam(exam);
        thread.setStudent(student);
        thread.setTitle(safeTitle);
        thread.setQuestionContent(safeContent);
        thread.setIsFaq(false);
        thread.setIsPinned(false);
        thread.setIsAnswered(false);
        thread = threadRepository.save(thread);

        ExamQnaMessage msg = new ExamQnaMessage();
        msg.setThread(thread);
        msg.setSender(student);
        msg.setSenderRole(student.getRole());
        msg.setContent(safeContent != null ? safeContent : safeTitle);
        messageRepository.save(msg);

        if (exam.getCreator() != null) {
            notificationService.createNotification(
                    exam.getCreator(),
                    "考试答疑：新提问",
                    "学生「" + (student.getFullName() != null ? student.getFullName() : student.getUsername()) +
                            "」在考试《" + exam.getTitle() + "》中发起了提问：" + safeTitle,
                    "EXAM_QNA",
                    thread.getId()
            );
        }

        return enrichThread(thread);
    }

    @Transactional
    public Map<String, Object> addMessage(Long threadId, String content, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ExamQnaThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));

        Exam exam = thread.getExam();

        if ("STUDENT".equals(user.getRole())) {
            if (!thread.getStudent().getId().equals(user.getId())) {
                throw new RuntimeException("无权在他人的提问中追加消息");
            }
            if (!canPostQuestion(exam)) {
                throw new RuntimeException("当前考试状态不允许追加消息");
            }
        } else if (!"TEACHER".equals(user.getRole()) && !"ADMIN".equals(user.getRole())) {
            throw new RuntimeException("权限不足");
        }

        String safeContent = sanitizeHtml(content);
        if (safeContent == null || safeContent.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }

        ExamQnaMessage msg = new ExamQnaMessage();
        msg.setThread(thread);
        msg.setSender(user);
        msg.setSenderRole(user.getRole());
        msg.setContent(safeContent);
        messageRepository.save(msg);

        if (("TEACHER".equals(user.getRole()) || "ADMIN".equals(user.getRole())) && !Boolean.TRUE.equals(thread.getIsAnswered())) {
            thread.setIsAnswered(true);
            thread.setAnsweredAt(LocalDateTime.now());
            thread.setAnsweredBy(user);
        }
        threadRepository.save(thread);

        if ("TEACHER".equals(user.getRole()) || "ADMIN".equals(user.getRole())) {
            notificationService.createNotification(
                    thread.getStudent(),
                    "考试答疑：教师已回复",
                    "您在考试《" + exam.getTitle() + "》中的提问「" + thread.getTitle() + "」已收到教师回复",
                    "EXAM_QNA_REPLY",
                    thread.getId()
            );
        } else if ("STUDENT".equals(user.getRole()) && exam.getCreator() != null) {
            notificationService.createNotification(
                    exam.getCreator(),
                    "考试答疑：学生追问",
                    "学生「" + (user.getFullName() != null ? user.getFullName() : user.getUsername()) +
                            "」在考试《" + exam.getTitle() + "》的提问中追加了消息",
                    "EXAM_QNA",
                    thread.getId()
            );
        }

        return enrichMessage(msg);
    }

    public List<Map<String, Object>> getThreadsForExam(Long examId, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        List<ExamQnaThread> threads;

        if ("TEACHER".equals(user.getRole()) || "ADMIN".equals(user.getRole())) {
            if (exam.getCreator() != null && exam.getCreator().getId().equals(user.getId())) {
                threads = threadRepository.findAllForTeacher(examId);
            } else if ("ADMIN".equals(user.getRole())) {
                threads = threadRepository.findAllForTeacher(examId);
            } else {
                threads = threadRepository.findVisibleForStudent(examId, user.getId());
            }
        } else {
            if (isExamEnded(exam)) {
                threads = threadRepository.findVisibleForStudent(examId, user.getId());
            } else {
                threads = threadRepository.findByExamAndStudentOrderByCreatedAtDesc(exam, user);
            }
        }

        return threads.stream().map(this::enrichThread).collect(Collectors.toList());
    }

    public Map<String, Object> getThreadDetail(Long threadId, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ExamQnaThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));

        Exam exam = thread.getExam();
        boolean isTeacher = "TEACHER".equals(user.getRole()) || "ADMIN".equals(user.getRole());
        boolean isExamCreator = exam.getCreator() != null && exam.getCreator().getId().equals(user.getId());
        boolean isOwner = thread.getStudent().getId().equals(user.getId());

        if (!isOwner && !isTeacher && !Boolean.TRUE.equals(thread.getIsFaq())) {
            throw new RuntimeException("无权查看此提问");
        }
        if (!isOwner && !isTeacher && Boolean.TRUE.equals(thread.getIsFaq()) && !isExamEnded(exam)) {
            if (!isOwner) {
                throw new RuntimeException("考试进行中仅可查看自己的提问");
            }
        }
        if (!isOwner && isTeacher && !isExamCreator && !"ADMIN".equals(user.getRole())) {
            if (!Boolean.TRUE.equals(thread.getIsFaq())) {
                throw new RuntimeException("无权查看此提问");
            }
        }

        Map<String, Object> result = enrichThread(thread);
        List<ExamQnaMessage> messages = messageRepository.findByThreadOrderByCreatedAtAsc(thread);
        result.put("messages", messages.stream().map(this::enrichMessage).collect(Collectors.toList()));
        return result;
    }

    @Transactional
    public Map<String, Object> markAsFaq(Long threadId, boolean isFaq, Principal principal) {
        assertTeacherOrAdmin(principal);
        ExamQnaThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));
        thread.setIsFaq(isFaq);
        threadRepository.save(thread);
        return enrichThread(thread);
    }

    @Transactional
    public Map<String, Object> togglePin(Long threadId, Principal principal) {
        assertTeacherOrAdmin(principal);
        ExamQnaThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));
        thread.setIsPinned(!Boolean.TRUE.equals(thread.getIsPinned()));
        threadRepository.save(thread);
        return enrichThread(thread);
    }

    public long getUnansweredCount(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if ("ADMIN".equals(user.getRole())) {
            return threadRepository.countUnanswered();
        } else if ("TEACHER".equals(user.getRole())) {
            return threadRepository.countUnansweredByTeacher(user.getId());
        }
        return 0;
    }

    public List<Map<String, Object>> getUnansweredThreads(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<ExamQnaThread> threads;
        if ("ADMIN".equals(user.getRole())) {
            threads = threadRepository.findAll().stream()
                    .filter(t -> !Boolean.TRUE.equals(t.getIsAnswered()))
                    .sorted(Comparator.comparing(ExamQnaThread::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        } else if ("TEACHER".equals(user.getRole())) {
            threads = threadRepository.findUnansweredByTeacher(user.getId());
        } else {
            threads = List.of();
        }
        return threads.stream().map(this::enrichThread).collect(Collectors.toList());
    }

    private void assertTeacherOrAdmin(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!"TEACHER".equals(user.getRole()) && !"ADMIN".equals(user.getRole())) {
            throw new RuntimeException("权限不足");
        }
    }

    private Map<String, Object> enrichThread(ExamQnaThread t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("questionContent", t.getQuestionContent());
        m.put("isFaq", t.getIsFaq());
        m.put("isPinned", t.getIsPinned());
        m.put("isAnswered", t.getIsAnswered());
        m.put("answeredAt", t.getAnsweredAt());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());

        if (t.getExam() != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", t.getExam().getId());
            e.put("title", t.getExam().getTitle());
            m.put("exam", e);
        }
        if (t.getStudent() != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", t.getStudent().getId());
            s.put("username", t.getStudent().getUsername());
            s.put("fullName", t.getStudent().getFullName());
            s.put("clazz", t.getStudent().getClazz());
            m.put("student", s);
        }
        if (t.getAnsweredBy() != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", t.getAnsweredBy().getId());
            a.put("fullName", t.getAnsweredBy().getFullName());
            m.put("answeredBy", a);
        }
        return m;
    }

    private Map<String, Object> enrichMessage(ExamQnaMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", msg.getId());
        m.put("threadId", msg.getThread() != null ? msg.getThread().getId() : null);
        m.put("content", msg.getContent());
        m.put("senderRole", msg.getSenderRole());
        m.put("createdAt", msg.getCreatedAt());

        if (msg.getSender() != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", msg.getSender().getId());
            s.put("username", msg.getSender().getUsername());
            s.put("fullName", msg.getSender().getFullName());
            m.put("sender", s);
        }
        return m;
    }
}
