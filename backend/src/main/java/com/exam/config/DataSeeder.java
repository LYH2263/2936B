package com.exam.config;

import com.exam.entity.*;
import com.exam.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    @Transactional
    public CommandLineRunner initData(UserRepository userRepository, 
                                      ExamRepository examRepository, 
                                      QuestionRepository questionRepository,
                                      ExamQuestionRepository examQuestionRepository,
                                      SubmissionRepository submissionRepository,
                                      SubmissionAnswerRepository submissionAnswerRepository,
                                      ExamTemplateRepository examTemplateRepository,
                                      ExamTemplateQuestionRepository examTemplateQuestionRepository,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Create Users if not exist
            User admin = null;
            User teacher1 = null;
            User teacher2 = null;
            
            if (userRepository.count() == 0) {
                admin = createUser(userRepository, passwordEncoder, "admin", "ADMIN", "管理员");
                teacher1 = createUser(userRepository, passwordEncoder, "1001", "TEACHER", "张老师");
                teacher2 = createUser(userRepository, passwordEncoder, "1002", "TEACHER", "王老师");
                createUser(userRepository, passwordEncoder, "2024001", "STUDENT", "李同学");
                createUser(userRepository, passwordEncoder, "2024002", "STUDENT", "陈同学");
            } else {
                admin = userRepository.findByUsername("admin").orElse(null);
                teacher1 = userRepository.findByUsername("1001").orElseGet(() -> 
                    createUser(userRepository, passwordEncoder, "1001", "TEACHER", "张老师"));
                // Ensure default numeric students exist if seeder runs again
                if (userRepository.findByUsername("2024001").isEmpty()) {
                    createUser(userRepository, passwordEncoder, "2024001", "STUDENT", "李同学");
                }
                
                // Backup logic: ensure all users have createdAt
                userRepository.findAll().forEach(u -> {
                    if (u.getCreatedAt() == null) {
                        u.setCreatedAt(java.time.LocalDateTime.now());
                        userRepository.save(u);
                    }
                });
                
                // Try to find or create auxiliary teachers if they don't exist
                teacher2 = userRepository.findByUsername("1002").orElseGet(() -> 
                    createUser(userRepository, passwordEncoder, "1002", "TEACHER", "王老师"));
            }

            // 2. Create Exams if not exist
            if (examRepository.count() == 0 && teacher1 != null) {
                createMathExam(examRepository, questionRepository, examQuestionRepository, teacher1);
                createScienceExam(examRepository, questionRepository, examQuestionRepository, teacher1);
                if (teacher2 != null) {
                    createHistoryExam(examRepository, questionRepository, examQuestionRepository, teacher2);
                    createTechExam(examRepository, questionRepository, examQuestionRepository, teacher2);
                }
                System.out.println("Data Seeding Completed with Realistic Chinese Data!");
            }

            if (examTemplateRepository.count() == 0 && teacher1 != null) {
                seedTemplates(examTemplateRepository, examTemplateQuestionRepository, questionRepository, teacher1, teacher2);
            }

            // 3. Seed Learning Alert Acceptance Test Data
            if (submissionRepository.count() < 5) {
                seedAlertAcceptanceData(userRepository, examRepository, questionRepository,
                        examQuestionRepository, submissionRepository, submissionAnswerRepository,
                        passwordEncoder, teacher1);
            }
        };
    }

    private void seedTemplates(ExamTemplateRepository tplRepo, ExamTemplateQuestionRepository tqRepo,
                               QuestionRepository qRepo, User teacher1, User teacher2) {
        List<Question> allQuestions = qRepo.findAll();

        ExamTemplate mathTpl = new ExamTemplate();
        mathTpl.setName("高等数学标准模板");
        mathTpl.setDescription("涵盖微积分、线性代数基础，适合期中期末考试");
        mathTpl.setCourse("数学");
        mathTpl.setVisibility("PUBLIC");
        mathTpl.setReviewStatus("APPROVED");
        mathTpl.setTags("数学,微积分,线性代数,期中");
        mathTpl.setDuration(90);
        mathTpl.setCreator(teacher1);
        mathTpl = tplRepo.save(mathTpl);

        List<Question> mathQs = allQuestions.stream()
                .filter(q -> q.getContent().contains("导数") || q.getContent().contains("交换律") || q.getContent().contains("三角函数"))
                .limit(3)
                .toList();
        for (int i = 0; i < mathQs.size(); i++) {
            ExamTemplateQuestion tq = new ExamTemplateQuestion();
            tq.setTemplate(mathTpl);
            tq.setQuestion(mathQs.get(i));
            tq.setScore(mathQs.get(i).getDefaultScore());
            tq.setSequence(i + 1);
            tqRepo.save(tq);
        }

        ExamTemplate historyTpl = new ExamTemplate();
        historyTpl.setName("中国古代史通用模板");
        historyTpl.setDescription("秦汉至明清历史变迁考察");
        historyTpl.setCourse("历史");
        historyTpl.setVisibility("PUBLIC");
        historyTpl.setReviewStatus("APPROVED");
        historyTpl.setTags("历史,古代史,秦汉");
        historyTpl.setDuration(45);
        historyTpl.setCreator(teacher2 != null ? teacher2 : teacher1);
        historyTpl = tplRepo.save(historyTpl);

        List<Question> historyQs = allQuestions.stream()
                .filter(q -> q.getContent().contains("秦始皇") || q.getContent().contains("唐朝") || q.getContent().contains("统一六国"))
                .limit(2)
                .toList();
        for (int i = 0; i < historyQs.size(); i++) {
            ExamTemplateQuestion tq = new ExamTemplateQuestion();
            tq.setTemplate(historyTpl);
            tq.setQuestion(historyQs.get(i));
            tq.setScore(historyQs.get(i).getDefaultScore());
            tq.setSequence(i + 1);
            tqRepo.save(tq);
        }

        ExamTemplate privateTpl = new ExamTemplate();
        privateTpl.setName("我的私有模板");
        privateTpl.setDescription("仅供个人使用的模板");
        privateTpl.setCourse("计算机");
        privateTpl.setVisibility("PRIVATE");
        privateTpl.setReviewStatus("APPROVED");
        privateTpl.setTags("计算机,基础");
        privateTpl.setDuration(120);
        privateTpl.setCreator(teacher1);
        tplRepo.save(privateTpl);

        System.out.println("Template seed data created!");
    }

    private User createUser(UserRepository repo, PasswordEncoder encoder, String username, String role, String fullName) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode("123456"));
        user.setRole(role);
        user.setFullName(fullName);
        return repo.save(user);
    }

    private void createMathExam(ExamRepository examRepo, QuestionRepository questionRepo, ExamQuestionRepository eqRepo, User creator) {
        Exam exam = new Exam();
        exam.setTitle("高等数学期中考试");
        exam.setDescription("本试卷涵盖微积分、线性代数基础知识。请在规定时间内完成。");
        exam.setCourse("数学");
        exam.setDuration(90);
        exam.setState("PUBLISHED");
        exam.setCreator(creator);
        exam.setStartTime(LocalDateTime.now().minusDays(1));
        exam.setEndTime(LocalDateTime.now().plusDays(7));
        exam.setCoverUrl("/covers/math.png");
        exam = examRepo.save(exam);

        List<Question> questions = new ArrayList<>();
        questions.add(createQuestion(questionRepo, creator, "SINGLE", "函数 f(x) = x^2 在 x=1 处的导数是？", 
            "[{\"label\":\"A\",\"text\":\"1\"},{\"label\":\"B\",\"text\":\"2\"},{\"label\":\"C\",\"text\":\"3\"},{\"label\":\"D\",\"text\":\"0\"}]", "B", "f'(x)=2x, f'(1)=2", 5, "导数与微分"));
        questions.add(createQuestion(questionRepo, creator, "JUDGE", "矩阵乘法满足交换律。", 
            null, "FALSE", "矩阵乘法一般不满足交换律 AB != BA", 5, "线性代数-矩阵运算"));
        questions.add(createQuestion(questionRepo, creator, "MULTI", "下列哪些是三角函数？", 
            "[{\"label\":\"A\",\"text\":\"sin(x)\"},{\"label\":\"B\",\"text\":\"cos(x)\"},{\"label\":\"C\",\"text\":\"log(x)\"},{\"label\":\"D\",\"text\":\"tan(x)\"}]", "ABD", null, 5, "三角函数"));

        linkQuestionsToExam(eqRepo, exam, questions);
    }

    private void createScienceExam(ExamRepository examRepo, QuestionRepository questionRepo, ExamQuestionRepository eqRepo, User creator) {
        Exam exam = new Exam();
        exam.setTitle("自然科学综合测试");
        exam.setDescription("测试物理、化学、生物基础概念。");
        exam.setDuration(60);
        exam.setState("PUBLISHED");
        exam.setCreator(creator);
        exam.setStartTime(LocalDateTime.now());
        exam.setEndTime(LocalDateTime.now().plusDays(30));
        exam.setCoverUrl("/covers/science.png");
        exam = examRepo.save(exam);

        List<Question> questions = new ArrayList<>();
        questions.add(createQuestion(questionRepo, creator, "SINGLE", "水分子的化学式是？", 
            "[{\"label\":\"A\",\"text\":\"HO\"},{\"label\":\"B\",\"text\":\"H2O\"},{\"label\":\"C\",\"text\":\"H2O2\"},{\"label\":\"D\",\"text\":\"OH\"}]", "B", "两个氢原子一个氧原子", 5));
        questions.add(createQuestion(questionRepo, creator, "SINGLE", "太阳系中最大的行星是？", 
            "[{\"label\":\"A\",\"text\":\"地球\"},{\"label\":\"B\",\"text\":\"火星\"},{\"label\":\"C\",\"text\":\"木星\"},{\"label\":\"D\",\"text\":\"土星\"}]", "C", "木星体积最大", 5));
            
        linkQuestionsToExam(eqRepo, exam, questions);
    }

    private void createHistoryExam(ExamRepository examRepo, QuestionRepository questionRepo, ExamQuestionRepository eqRepo, User creator) {
        Exam exam = new Exam();
        exam.setTitle("中国古代史");
        exam.setDescription("考察秦汉至明清的历史变迁。");
        exam.setDuration(45);
        exam.setState("PUBLISHED");
        exam.setCreator(creator);
        exam.setStartTime(LocalDateTime.now().minusDays(5));
        exam.setEndTime(LocalDateTime.now().plusDays(5));
        exam.setCoverUrl("/covers/history.png");
        exam = examRepo.save(exam);
        
        List<Question> questions = new ArrayList<>();
        questions.add(createQuestion(questionRepo, creator, "SINGLE", "秦始皇统一六国的时间是？", 
            "[{\"label\":\"A\",\"text\":\"公元前221年\"},{\"label\":\"B\",\"text\":\"公元221年\"},{\"label\":\"C\",\"text\":\"公元前202年\"},{\"label\":\"D\",\"text\":\"公元202年\"}]", "A", null, 10));
        questions.add(createQuestion(questionRepo, creator, "JUDGE", "唐朝的开国皇帝是李世民。", 
            null, "FALSE", "是李渊", 5));

        linkQuestionsToExam(eqRepo, exam, questions);
    }

    private void createTechExam(ExamRepository examRepo, QuestionRepository questionRepo, ExamQuestionRepository eqRepo, User creator) {
        Exam exam = new Exam();
        exam.setTitle("计算机基础知识");
        exam.setDescription("计算机组成原理、网络、操作系统基础。");
        exam.setDuration(120);
        exam.setState("PUBLISHED");
        exam.setCreator(creator);
        exam = examRepo.save(exam);
        exam.setCoverUrl("/covers/tech.png");
        exam = examRepo.save(exam);
        
        List<Question> questions = new ArrayList<>();
        questions.add(createQuestion(questionRepo, creator, "SINGLE", "下图所示的电路元件符号代表什么？<br><img src='/images/circuit.png' style='max-width:300px;' />", 
            "[{\"label\":\"A\",\"text\":\"电阻\"},{\"label\":\"B\",\"text\":\"电容\"},{\"label\":\"C\",\"text\":\"灯泡\"},{\"label\":\"D\",\"text\":\"开关\"}]", "D", null, 5));
        questions.add(createQuestion(questionRepo, creator, "MULTI", "下列属于操作系统的是？", 
            "[{\"label\":\"A\",\"text\":\"Windows\"},{\"label\":\"B\",\"text\":\"Linux\"},{\"label\":\"C\",\"text\":\"Office\"},{\"label\":\"D\",\"text\":\"macOS\"}]", "ABD", null, 5));

        linkQuestionsToExam(eqRepo, exam, questions);
    }

    private Question createQuestion(QuestionRepository repo, User creator, String type, String content, String options, String answer, String analysis, int score) {
        return createQuestion(repo, creator, type, content, options, answer, analysis, score, null);
    }

    private Question createQuestion(QuestionRepository repo, User creator, String type, String content, String options, String answer, String analysis, int score, String knowledgePoint) {
        Question q = new Question();
        q.setCreator(creator);
        q.setType(type);
        q.setContent(content);
        q.setOptions(options);
        q.setAnswer(answer);
        q.setAnalysis(analysis);
        q.setDefaultScore(score);
        q.setKnowledgePoint(knowledgePoint);
        return repo.save(q);
    }

    private void linkQuestionsToExam(ExamQuestionRepository eqRepo, Exam exam, List<Question> questions) {
        for (int i = 0; i < questions.size(); i++) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExam(exam);
            eq.setQuestion(questions.get(i));
            eq.setScore(questions.get(i).getDefaultScore());
            eq.setSequence(i + 1);
            eqRepo.save(eq);
        }
    }

    private void seedAlertAcceptanceData(UserRepository userRepo, ExamRepository examRepo,
                                          QuestionRepository qRepo, ExamQuestionRepository eqRepo,
                                          SubmissionRepository subRepo, SubmissionAnswerRepository saRepo,
                                          PasswordEncoder encoder, User teacher) {
        System.out.println("Seeding Learning Alert Acceptance Test Data...");

        User weakStudent = userRepo.findByUsername("2024999").orElseGet(() -> {
            User u = new User();
            u.setUsername("2024999");
            u.setPassword(encoder.encode("123456"));
            u.setRole("STUDENT");
            u.setFullName("困难生小明");
            u.setClazz("预警测试班");
            u.setCreatedAt(LocalDateTime.now().minusDays(100));
            return userRepo.save(u);
        });

        User absentStudent = userRepo.findByUsername("2024998").orElseGet(() -> {
            User u = new User();
            u.setUsername("2024998");
            u.setPassword(encoder.encode("123456"));
            u.setRole("STUDENT");
            u.setFullName("长期缺考小华");
            u.setClazz("预警测试班");
            u.setCreatedAt(LocalDateTime.now().minusDays(100));
            return userRepo.save(u);
        });

        List<Exam> allExams = examRepo.findAll();
        Exam mathExam = allExams.stream().filter(e -> "数学".equals(e.getCourse())).findFirst()
                .orElse(allExams.isEmpty() ? null : allExams.get(0));

        if (mathExam == null) {
            return;
        }

        Exam alertExam1 = new Exam();
        alertExam1.setTitle("预警测试-第一次月考（数学）");
        alertExam1.setCourse("数学");
        alertExam1.setDescription("学情预警验收测试：第一次低分考试");
        alertExam1.setDuration(90);
        alertExam1.setState("ENDED");
        alertExam1.setCreator(teacher);
        alertExam1.setStartTime(LocalDateTime.now().minusDays(20));
        alertExam1.setEndTime(LocalDateTime.now().minusDays(19));
        alertExam1 = examRepo.save(alertExam1);

        Exam alertExam2 = new Exam();
        alertExam2.setTitle("预警测试-第二次月考（数学）");
        alertExam2.setCourse("数学");
        alertExam2.setDescription("学情预警验收测试：第二次连续低分考试");
        alertExam2.setDuration(90);
        alertExam2.setState("ENDED");
        alertExam2.setCreator(teacher);
        alertExam2.setStartTime(LocalDateTime.now().minusDays(6));
        alertExam2.setEndTime(LocalDateTime.now().minusDays(5));
        alertExam2 = examRepo.save(alertExam2);

        List<Question> kpQuestions = new ArrayList<>();
        String targetKP = "一元二次方程";
        for (int i = 0; i < 5; i++) {
            Question q = new Question();
            q.setCreator(teacher);
            q.setType("SINGLE");
            q.setContent("一元二次方程测试题 #" + (i + 1) + "：方程 x^2 + " + (i+1) + "x + " + i + " = 0 的解是？");
            q.setOptions("[{\"label\":\"A\",\"text\":\"x=1\"},{\"label\":\"B\",\"text\":\"x=-1\"},{\"label\":\"C\",\"text\":\"x=2\"},{\"label\":\"D\",\"text\":\"无解\"}]");
            q.setAnswer("A");
            q.setAnalysis("标准一元二次方程求解");
            q.setDefaultScore(10);
            q.setKnowledgePoint(targetKP);
            q.setSubject("数学");
            kpQuestions.add(qRepo.save(q));
        }

        Question trigQ = createQuestion(qRepo, teacher, "SINGLE",
                "sin(30°) 的值是？",
                "[{\"label\":\"A\",\"text\":\"1/2\"},{\"label\":\"B\",\"text\":\"√2/2\"},{\"label\":\"C\",\"text\":\"√3/2\"},{\"label\":\"D\",\"text\":\"1\"}]",
                "A", "sin(30°)=1/2", 10, "三角函数");
        Question derivQ = createQuestion(qRepo, teacher, "SINGLE",
                "f(x)=x^3 的导数 f'(x) = ?",
                "[{\"label\":\"A\",\"text\":\"3x^2\"},{\"label\":\"B\",\"text\":\"x^2\"},{\"label\":\"C\",\"text\":\"3x\"},{\"label\":\"D\",\"text\":\"x^3\"}]",
                "A", "幂函数求导法则", 10, "导数与微分");
        Question matrixQ = createQuestion(qRepo, teacher, "JUDGE",
                "单位矩阵乘以任何矩阵等于原矩阵。",
                null, "TRUE", "单位矩阵性质", 10, "线性代数-矩阵运算");

        List<Question> allAlertQs = new ArrayList<>(kpQuestions);
        allAlertQs.add(trigQ);
        allAlertQs.add(derivQ);
        allAlertQs.add(matrixQ);
        linkQuestionsToExam(eqRepo, alertExam1, allAlertQs);
        linkQuestionsToExam(eqRepo, alertExam2, allAlertQs);

        int totalScore = allAlertQs.stream().mapToInt(Question::getDefaultScore).sum();
        int lowScore = (int) Math.floor(totalScore * 0.4);

        Submission sub1 = new Submission();
        sub1.setStudent(weakStudent);
        sub1.setExam(alertExam1);
        sub1.setStartTime(LocalDateTime.now().minusDays(20));
        sub1.setEndTime(LocalDateTime.now().minusDays(19).plusHours(1));
        sub1.setState("SUBMITTED");
        sub1.setScore(lowScore);
        sub1 = subRepo.save(sub1);

        for (Question q : allAlertQs) {
            SubmissionAnswer sa = new SubmissionAnswer();
            sa.setSubmission(sub1);
            sa.setQuestion(q);
            if (targetKP.equals(q.getKnowledgePoint())) {
                sa.setScore(0);
                sa.setStudentAnswer("WRONG_" + q.getId());
            } else if ("三角函数".equals(q.getKnowledgePoint())) {
                sa.setScore(0);
            } else {
                sa.setScore(q.getDefaultScore());
                sa.setStudentAnswer(q.getAnswer());
            }
            saRepo.save(sa);
        }

        Submission sub2 = new Submission();
        sub2.setStudent(weakStudent);
        sub2.setExam(alertExam2);
        sub2.setStartTime(LocalDateTime.now().minusDays(6));
        sub2.setEndTime(LocalDateTime.now().minusDays(5).plusHours(1));
        sub2.setState("SUBMITTED");
        sub2.setScore(lowScore - 5);
        sub2 = subRepo.save(sub2);

        for (Question q : allAlertQs) {
            SubmissionAnswer sa = new SubmissionAnswer();
            sa.setSubmission(sub2);
            sa.setQuestion(q);
            if (targetKP.equals(q.getKnowledgePoint())) {
                sa.setScore(0);
                sa.setStudentAnswer("WRONG2_" + q.getId());
            } else {
                sa.setScore(Math.max(0, q.getDefaultScore() - 5));
            }
            saRepo.save(sa);
        }

        absentStudent.setCreatedAt(LocalDateTime.now().minusDays(100));
        userRepo.save(absentStudent);

        System.out.println("Alert Acceptance Data Seeded! 已创建学生「困难生小明(2024999)」两次考试低于及格线 + 知识点「一元二次方程」低于40%；学生「长期缺考小华(2024998)」注册100天未参加考试。密码均为 123456。");
    }
}
