package com.exam.config;

import com.exam.entity.*;
import com.exam.repository.*;
import com.exam.service.ExamVersionService;
import com.exam.service.LearningAlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

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
                                      ClazzRepository clazzRepository,
                                      ClazzStudentRepository clazzStudentRepository,
                                      WrongQuestionBookRepository wrongQuestionBookRepository,
                                      ExamQnaThreadRepository examQnaThreadRepository,
                                      ExamQnaMessageRepository examQnaMessageRepository,
                                      ExamTimeSlotRepository examTimeSlotRepository,
                                      ExamReservationRepository examReservationRepository,
                                      ExamVersionRepository examVersionRepository,
                                      AnswerSnapshotRepository answerSnapshotRepository,
                                      PkSessionRepository pkSessionRepository,
                                      PkAnswerRepository pkAnswerRepository,
                                      LearningAlertRepository learningAlertRepository,
                                      ExamVersionService examVersionService,
                                      LearningAlertService learningAlertService,
                                      ObjectMapper objectMapper,
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
                if (userRepository.findByUsername("2024002").isEmpty()) {
                    createUser(userRepository, passwordEncoder, "2024002", "STUDENT", "陈同学");
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

            // 4. Seed Grading Workbench Acceptance Test Data
            seedGradingWorkbenchData(userRepository, examRepository, questionRepository,
                    examQuestionRepository, submissionRepository, submissionAnswerRepository,
                    passwordEncoder, teacher1);

            // 5. Seed Class Management Acceptance Test Data
            seedClassManagementData(userRepository, examRepository, questionRepository,
                    examQuestionRepository, clazzRepository, clazzStudentRepository,
                    passwordEncoder, teacher1, teacher2);

            // 6. Seed Wrong Question Book Test Data
            seedWrongQuestionBookData(userRepository, examRepository, questionRepository,
                    examQuestionRepository, submissionRepository, submissionAnswerRepository,
                    wrongQuestionBookRepository);

            // 7. Seed Exam Q&A Test Data
            seedExamQnaData(userRepository, examRepository, examQnaThreadRepository,
                    examQnaMessageRepository, teacher1);

            // 8. Seed Reservation Queue Test Data
            seedReservationData(userRepository, examRepository, questionRepository,
                    examQuestionRepository, examTimeSlotRepository, examReservationRepository,
                    passwordEncoder, teacher1);

            // 9. Seed Exam Version & Answer Replay Test Data
            seedExamVersionAndReplayData(examRepository, questionRepository, examQuestionRepository,
                    submissionRepository, examVersionRepository, answerSnapshotRepository,
                    examVersionService, objectMapper, teacher1);

            // 10. Seed PK Battle History Test Data
            seedPkBattleData(userRepository, questionRepository, pkSessionRepository,
                    pkAnswerRepository, objectMapper);

            // 11. Generate Learning Alerts from seeded submissions
            if (learningAlertRepository.countUnresolved() == 0) {
                learningAlertService.runFullScan();
                System.out.println("Learning alerts generated from test submissions!");
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

    private void seedGradingWorkbenchData(UserRepository userRepo, ExamRepository examRepo,
                                          QuestionRepository qRepo, ExamQuestionRepository eqRepo,
                                          SubmissionRepository subRepo, SubmissionAnswerRepository saRepo,
                                          PasswordEncoder encoder, User teacher) {
        String gradingExamTitle = "期末模拟考试（简答题专练）";
        if (examRepo.findAll().stream().anyMatch(e -> gradingExamTitle.equals(e.getTitle()))) {
            return;
        }

        System.out.println("Seeding Grading Workbench Acceptance Test Data...");

        Exam gradingExam = new Exam();
        gradingExam.setTitle(gradingExamTitle);
        gradingExam.setDescription("本试卷包含5道简答题，用于测试批量批改工作台功能。");
        gradingExam.setCourse("综合练习");
        gradingExam.setDuration(60);
        gradingExam.setState("PUBLISHED");
        gradingExam.setCreator(teacher);
        gradingExam.setStartTime(LocalDateTime.now().minusDays(2));
        gradingExam.setEndTime(LocalDateTime.now().plusDays(5));
        gradingExam = examRepo.save(gradingExam);

        List<Question> shortQuestions = new ArrayList<>();
        String[] qContents = {
            "请简述数据库事务的ACID特性及其含义。",
            "什么是RESTful API？请列举其主要特点。",
            "请解释面向对象编程中的多态概念，并举例说明。",
            "简述HTTP与HTTPS的主要区别。",
            "请描述你对微服务架构的理解，以及它与单体架构的优缺点对比。"
        };
        String[] rubrics = {
            "评分标准：\n1. 完整说出ACID四个字母含义（2分）\n2. 每个特性解释清晰准确（各1分，共4分）\n3. 能举出实际例子说明（2分）\n4. 表述逻辑清晰（2分）",
            "评分标准：\n1. 正确定义RESTful API（2分）\n2. 说出3个以上主要特点（每个1分，共5分）\n3. 理解HTTP方法的语义（3分）",
            "评分标准：\n1. 正确定义多态（2分）\n2. 区分编译时多态和运行时多态（3分）\n3. 给出Java代码示例（3分）\n4. 说明多态的好处（2分）",
            "评分标准：\n1. 说出HTTPS = HTTP + SSL/TLS（2分）\n2. 列出3个以上区别（每个2分，共6分）\n3. 说明端口号差异（2分）",
            "评分标准：\n1. 正确定义微服务（2分）\n2. 说出微服务的3个优点（3分）\n3. 说出微服务的3个缺点（3分）\n4. 与单体架构对比清晰（2分）"
        };

        for (int i = 0; i < qContents.length; i++) {
            Question q = new Question();
            q.setCreator(teacher);
            q.setType("SHORT");
            q.setContent(qContents[i]);
            q.setAnswer("参考课堂笔记");
            q.setAnalysis(rubrics[i]);
            q.setDefaultScore(10);
            q.setSubject("计算机");
            q.setKnowledgePoint("简答题" + (i + 1));
            q.setDifficulty(3);
            shortQuestions.add(qRepo.save(q));
        }

        for (int i = 0; i < shortQuestions.size(); i++) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExam(gradingExam);
            eq.setQuestion(shortQuestions.get(i));
            eq.setScore(10);
            eq.setSequence(i + 1);
            eqRepo.save(eq);
        }

        String[] studentNames = {
            "张三", "李四", "王五", "赵六", "钱七",
            "孙八", "周九", "吴十", "郑十一", "王十二",
            "冯十三", "陈十四", "褚十五", "卫十六", "蒋十七",
            "沈十八", "韩十九", "杨二十", "朱廿一", "秦廿二"
        };

        String[][] sampleAnswers = {
            {
                "ACID是原子性(Atomicity)、一致性(Consistency)、隔离性(Isolation)、持久性(Durability)的缩写。原子性指事务是不可分割的工作单位；一致性指事务必须使数据库从一个一致性状态变到另一个一致性状态；隔离性指多个事务并发执行时互不干扰；持久性指事务一旦提交，对数据的改变就是永久性的。",
                "RESTful API是一种基于REST架构风格的应用程序接口。主要特点包括：1.使用HTTP方法表达操作语义（GET查询、POST创建、PUT更新、DELETE删除）；2.无状态，每个请求都包含所有必要信息；3.资源通过URI标识；4.支持多种数据格式，常用JSON；5.可缓存性。",
                "多态是面向对象编程的三大特性之一，指同一个方法调用可以根据对象的不同而有不同的行为。多态分为编译时多态（方法重载）和运行时多态（方法重写）。例如，父类Animal有一个eat()方法，子类Dog和Cat都重写了这个方法，当用Animal类型的变量引用不同子类对象时，调用eat()会表现出不同的行为。",
                "HTTP和HTTPS的主要区别有：1.HTTPS比HTTP多了SSL/TLS加密层；2.HTTP默认端口80，HTTPS默认端口443；3.HTTPS需要CA证书，有一定成本；4.HTTPS更安全，能防止数据被窃听和篡改；5.HTTPS会稍微影响加载速度，但现在可以忽略不计；6.HTTPS对SEO更友好。",
                "微服务架构是将一个大型应用拆分为多个小型、独立的服务，每个服务运行在自己的进程中，通过轻量级机制通信。优点：独立部署、技术栈灵活、易于扩展、故障隔离、团队自治。缺点：运维复杂度高、分布式事务困难、服务间调用延迟、测试难度大。单体架构则相反，开发简单部署方便，但扩展性差、技术栈固定、单点故障影响大。"
            },
            {
                "ACID就是四个特性：原子性、一致性、隔离性、持久性。具体意思我记不太清了，大概就是事务要保证数据的正确性吧。",
                "RESTful API是一种API设计风格，用HTTP的方法来做不同的操作。好像还有资源的概念，用URL表示资源。",
                "多态就是同一个方法有不同的实现。比如子类可以重写父类的方法。具体的我也不太会解释。",
                "HTTPS比HTTP安全，因为加了个S，应该是加密的意思。别的区别我不太清楚。",
                "微服务就是把系统拆成小服务，每个服务单独部署。比单体架构灵活，但是好像更复杂。"
            }
        };

        for (int i = 0; i < 20; i++) {
            final int studentIdx = i;
            User student = userRepo.findByUsername("2024" + String.format("%03d", 100 + i)).orElseGet(() -> {
                User u = new User();
                u.setUsername("2024" + String.format("%03d", 100 + studentIdx));
                u.setPassword(encoder.encode("123456"));
                u.setRole("STUDENT");
                u.setFullName(studentNames[studentIdx]);
                u.setClazz("批改测试班");
                u.setCreatedAt(LocalDateTime.now().minusDays(30));
                return userRepo.save(u);
            });

            Submission sub = new Submission();
            sub.setExam(gradingExam);
            sub.setStudent(student);
            sub.setStartTime(LocalDateTime.now().minusDays(1).plusHours(i / 4));
            sub.setEndTime(LocalDateTime.now().minusDays(1).plusHours(i / 4).plusMinutes(45));
            sub.setState("SUBMITTED");
            sub.setScore(0);
            sub = subRepo.save(sub);

            int answerQuality = i < 10 ? 0 : 1;
            for (int qIdx = 0; qIdx < shortQuestions.size(); qIdx++) {
                SubmissionAnswer sa = new SubmissionAnswer();
                sa.setSubmission(sub);
                sa.setQuestion(shortQuestions.get(qIdx));
                sa.setStudentAnswer(sampleAnswers[answerQuality][qIdx]);
                sa.setScore(null);
                saRepo.save(sa);
            }
        }

        System.out.println("Grading Workbench Test Data Seeded! 已创建「期末模拟考试（简答题专练）」考试，包含5道简答题、20名学生答卷。登录教师账号 1001/123456 进入批改工作台体验。");
    }

    private void seedClassManagementData(UserRepository userRepo, ExamRepository examRepo,
                                          QuestionRepository qRepo, ExamQuestionRepository eqRepo,
                                          ClazzRepository clazzRepo, ClazzStudentRepository csRepo,
                                          PasswordEncoder encoder, User teacher1, User teacher2) {
        String targetClassName = "软件2201班";
        if (clazzRepo.existsByName(targetClassName)) {
            return;
        }

        System.out.println("Seeding Class Management Acceptance Test Data...");

        com.exam.entity.Clazz clazz2201 = new com.exam.entity.Clazz();
        clazz2201.setName(targetClassName);
        clazz2201.setGrade("2022级");
        clazz2201.setTeacher(teacher1);
        clazz2201 = clazzRepo.save(clazz2201);

        com.exam.entity.Clazz clazz2202 = new com.exam.entity.Clazz();
        clazz2202.setName("软件2202班");
        clazz2202.setGrade("2022级");
        clazz2202.setTeacher(teacher2);
        clazz2202 = clazzRepo.save(clazz2202);

        String[][] studentData = {
            {"2022001", "张小明", "软件2201班"},
            {"2022002", "李小红", "软件2201班"},
            {"2022003", "王小强", "软件2201班"},
            {"2022004", "赵小丽", "软件2201班"},
            {"2022005", "陈小伟", "软件2201班"},
            {"2022006", "刘小芳", "软件2202班"},
            {"2022007", "周小军", "软件2202班"},
            {"2022008", "吴小燕", "软件2202班"},
        };

        for (String[] data : studentData) {
            String username = data[0];
            String fullName = data[1];
            String className = data[2];

            User student = userRepo.findByUsername(username).orElseGet(() -> {
                User u = new User();
                u.setUsername(username);
                u.setFullName(fullName);
                u.setPassword(encoder.encode("123456"));
                u.setRole("STUDENT");
                u.setClazz(className);
                u.setCreatedAt(java.time.LocalDateTime.now().minusDays(100));
                return userRepo.save(u);
            });

            com.exam.entity.Clazz targetClazz = "软件2201班".equals(className) ? clazz2201 : clazz2202;

            if (!csRepo.existsByClazzIdAndStudentId(targetClazz.getId(), student.getId())) {
                com.exam.entity.ClazzStudent cs = new com.exam.entity.ClazzStudent();
                cs.setClazz(targetClazz);
                cs.setStudent(student);
                csRepo.save(cs);
            }
        }

        Exam classExam = new Exam();
        classExam.setTitle("软件2201班专属单元测试");
        classExam.setDescription("本考试仅对软件2201班学生开放。");
        classExam.setCourse("Java程序设计");
        classExam.setDuration(60);
        classExam.setState("PUBLISHED");
        classExam.setCreator(teacher1);
        classExam.setStartTime(java.time.LocalDateTime.now());
        classExam.setEndTime(java.time.LocalDateTime.now().plusDays(14));
        classExam.setTargetAudience("CLASS");
        classExam.setTargetClassIds(String.valueOf(clazz2201.getId()));
        classExam.setCoverUrl("/covers/tech.png");
        classExam = examRepo.save(classExam);

        List<Question> classExamQuestions = new ArrayList<>();
        classExamQuestions.add(createQuestion(qRepo, teacher1, "SINGLE", "Java中，以下哪个关键字用于定义类？", 
            "[{\"label\":\"A\",\"text\":\"function\"},{\"label\":\"B\",\"text\":\"class\"},{\"label\":\"C\",\"text\":\"struct\"},{\"label\":\"D\",\"text\":\"type\"}]", "B", "Java使用class关键字定义类", 10, "Java基础"));
        classExamQuestions.add(createQuestion(qRepo, teacher1, "SINGLE", "Java中，main方法的签名是？", 
            "[{\"label\":\"A\",\"text\":\"public void main(String[] args)\"},{\"label\":\"B\",\"text\":\"public static void main(String[] args)\"},{\"label\":\"C\",\"text\":\"static void main(String args)\"},{\"label\":\"D\",\"text\":\"void main()\"}]", "B", "main方法必须是public static void", 10, "Java基础"));
        classExamQuestions.add(createQuestion(qRepo, teacher1, "JUDGE", "Java支持多继承。", 
            null, "FALSE", "Java只支持单继承，可以实现多个接口", 5, "面向对象"));

        linkQuestionsToExam(eqRepo, classExam, classExamQuestions);

        System.out.println("Class Management Test Data Seeded!");
        System.out.println("已创建班级：软件2201班（张老师班主任，5名学生）、软件2202班（王老师班主任，3名学生）");
        System.out.println("已创建考试：「软件2201班专属单元测试」，仅软件2201班可见");
        System.out.println("测试账号：2022001-2022005（软件2201班），2022006-2022008（软件2202班），密码均为 123456");
    }

    private Submission buildSubmittedExam(SubmissionRepository subRepo, User student, Exam exam,
                                          List<ExamQuestion> examQuestions, double scoreRate) {
        Submission s = new Submission();
        s.setStudent(student);
        s.setExam(exam);
        s.setStartTime(LocalDateTime.now().minusHours(2));
        s.setEndTime(LocalDateTime.now().minusHours(1));
        s.setState("SUBMITTED");
        int total = examQuestions.stream().mapToInt(ExamQuestion::getScore).sum();
        s.setScore((int) Math.floor(total * scoreRate));
        return subRepo.save(s);
    }

    private void seedWrongQuestionBookData(UserRepository userRepo, ExamRepository examRepo,
                                           QuestionRepository qRepo, ExamQuestionRepository eqRepo,
                                           SubmissionRepository subRepo, SubmissionAnswerRepository saRepo,
                                           WrongQuestionBookRepository wqbRepo) {
        if (wqbRepo.count() > 0) {
            return;
        }

        System.out.println("Seeding Wrong Question Book Test Data...");

        User student = userRepo.findByUsername("2024001").orElse(null);
        User weakStudent = userRepo.findByUsername("2024999").orElse(null);
        if (student == null && weakStudent == null) {
            return;
        }

        Exam mathExam = examRepo.findAll().stream()
                .filter(e -> "高等数学期中考试".equals(e.getTitle()))
                .findFirst().orElse(null);

        if (mathExam != null && student != null) {
            List<ExamQuestion> examQuestions = eqRepo.findByExamIdOrderBySequenceAsc(mathExam.getId());
            List<Submission> existingSubs = subRepo.findByExamIdAndStudentIdAndState(
                    mathExam.getId(), student.getId(), "SUBMITTED");
            Submission sub = existingSubs.isEmpty()
                    ? buildSubmittedExam(subRepo, student, mathExam, examQuestions, 0.6)
                    : existingSubs.get(0);

            for (int i = 0; i < examQuestions.size(); i++) {
                ExamQuestion eq = examQuestions.get(i);
                Question q = eq.getQuestion();
                boolean wrong = i % 2 == 0;
                String studentAnswer = wrong ? "B" : q.getAnswer();
                int scoreGot = wrong ? 0 : eq.getScore();

                SubmissionAnswer sa = new SubmissionAnswer();
                sa.setSubmission(sub);
                sa.setQuestion(q);
                sa.setStudentAnswer(studentAnswer);
                sa.setScore(scoreGot);
                saRepo.save(sa);

                if (wrong) {
                    WrongQuestionBook wqb = new WrongQuestionBook();
                    wqb.setStudent(student);
                    wqb.setQuestion(q);
                    wqb.setSubmission(sub);
                    wqb.setStudentAnswer(studentAnswer);
                    wqb.setScoreGot(0);
                    wqb.setFullScore(eq.getScore());
                    wqb.setWrongReason("概念理解错误");
                    wqb.setMastered(i == 0);
                    wqb.setWrongCount(i == 0 ? 2 : 1);
                    wqb.setAddedAt(LocalDateTime.now().minusDays(3 - i));
                    wqb.setLastWrongAt(LocalDateTime.now().minusDays(1));
                    wqbRepo.save(wqb);
                }
            }
        }

        if (weakStudent != null) {
            List<Submission> weakSubs = subRepo.findSubmittedByStudentOrderByEndTimeDesc(weakStudent.getId());
            Submission weakSub = weakSubs.isEmpty() ? null : weakSubs.get(0);

            if (weakSub != null) {
                List<ExamQuestion> alertQuestions = eqRepo.findByExamIdOrderBySequenceAsc(weakSub.getExam().getId());
                for (ExamQuestion eq : alertQuestions) {
                    Question q = eq.getQuestion();
                    if (q.getKnowledgePoint() == null || !q.getKnowledgePoint().contains("一元二次")) {
                        continue;
                    }
                    WrongQuestionBook wqb = new WrongQuestionBook();
                    wqb.setStudent(weakStudent);
                    wqb.setQuestion(q);
                    wqb.setSubmission(weakSub);
                    wqb.setStudentAnswer("WRONG_" + q.getId());
                    wqb.setScoreGot(0);
                    wqb.setFullScore(eq.getScore());
                    wqb.setWrongReason("一元二次方程掌握不足");
                    wqb.setMastered(false);
                    wqb.setWrongCount(2);
                    wqb.setAddedAt(LocalDateTime.now().minusDays(20));
                    wqb.setLastWrongAt(LocalDateTime.now().minusDays(5));
                    wqbRepo.save(wqb);
                }
            }
        }

        System.out.println("Wrong Question Book Test Data Seeded! 登录 2024001/123456 或 2024999/123456 查看错题本。");
    }

    private void seedExamQnaData(UserRepository userRepo, ExamRepository examRepo,
                                 ExamQnaThreadRepository threadRepo, ExamQnaMessageRepository msgRepo,
                                 User teacher) {
        if (threadRepo.count() > 0 || teacher == null) {
            return;
        }

        System.out.println("Seeding Exam Q&A Test Data...");

        Exam mathExam = examRepo.findAll().stream()
                .filter(e -> "高等数学期中考试".equals(e.getTitle()))
                .findFirst().orElse(null);
        if (mathExam == null) {
            return;
        }

        User student1 = userRepo.findByUsername("2024001").orElse(null);
        User student2 = userRepo.findByUsername("2024002").orElse(null);
        if (student1 == null) {
            return;
        }

        ExamQnaThread faqThread = new ExamQnaThread();
        faqThread.setExam(mathExam);
        faqThread.setStudent(student1);
        faqThread.setTitle("【FAQ】考试注意事项与允许携带物品");
        faqThread.setQuestionContent("本考试是否允许使用计算器？考试时长如何计算？");
        faqThread.setIsFaq(true);
        faqThread.setIsPinned(true);
        faqThread.setIsAnswered(true);
        faqThread.setAnsweredAt(LocalDateTime.now().minusDays(2));
        faqThread.setAnsweredBy(teacher);
        faqThread.setCreatedAt(LocalDateTime.now().minusDays(3));
        faqThread.setUpdatedAt(LocalDateTime.now().minusDays(2));
        faqThread = threadRepo.save(faqThread);

        ExamQnaMessage faqReply = new ExamQnaMessage();
        faqReply.setThread(faqThread);
        faqReply.setSender(teacher);
        faqReply.setSenderRole("TEACHER");
        faqReply.setContent("本次考试<strong>不允许</strong>使用计算器。考试时长为90分钟，从点击「开始考试」时计时，超时系统自动提交。");
        faqReply.setCreatedAt(LocalDateTime.now().minusDays(2));
        msgRepo.save(faqReply);

        ExamQnaThread answeredThread = new ExamQnaThread();
        answeredThread.setExam(mathExam);
        answeredThread.setStudent(student1);
        answeredThread.setTitle("矩阵乘法那道题有点不理解");
        answeredThread.setQuestionContent("老师您好，矩阵乘法不满足交换律，那什么情况下 AB = BA 呢？");
        answeredThread.setIsAnswered(true);
        answeredThread.setAnsweredAt(LocalDateTime.now().minusHours(5));
        answeredThread.setAnsweredBy(teacher);
        answeredThread.setCreatedAt(LocalDateTime.now().minusDays(1));
        answeredThread.setUpdatedAt(LocalDateTime.now().minusHours(5));
        answeredThread = threadRepo.save(answeredThread);

        ExamQnaMessage studentQ1 = new ExamQnaMessage();
        studentQ1.setThread(answeredThread);
        studentQ1.setSender(student1);
        studentQ1.setSenderRole("STUDENT");
        studentQ1.setContent("老师您好，矩阵乘法不满足交换律，那什么情况下 AB = BA 呢？");
        studentQ1.setCreatedAt(LocalDateTime.now().minusDays(1));
        msgRepo.save(studentQ1);

        ExamQnaMessage teacherA1 = new ExamQnaMessage();
        teacherA1.setThread(answeredThread);
        teacherA1.setSender(teacher);
        teacherA1.setSenderRole("TEACHER");
        teacherA1.setContent("当 A、B 同时为对角矩阵，或其中一个为单位矩阵时，一般有 AB = BA。更严格地说，当 AB - BA = 0 时称两矩阵可交换。");
        teacherA1.setCreatedAt(LocalDateTime.now().minusHours(5));
        msgRepo.save(teacherA1);

        if (student2 != null) {
            ExamQnaThread pendingThread = new ExamQnaThread();
            pendingThread.setExam(mathExam);
            pendingThread.setStudent(student2);
            pendingThread.setTitle("导数计算题能否写详细步骤？");
            pendingThread.setQuestionContent("求导题目需要写出完整推导过程吗，还是只写最终答案即可？");
            pendingThread.setIsAnswered(false);
            pendingThread.setCreatedAt(LocalDateTime.now().minusHours(3));
            pendingThread.setUpdatedAt(LocalDateTime.now().minusHours(3));
            pendingThread = threadRepo.save(pendingThread);

            ExamQnaMessage studentQ2 = new ExamQnaMessage();
            studentQ2.setThread(pendingThread);
            studentQ2.setSender(student2);
            studentQ2.setSenderRole("STUDENT");
            studentQ2.setContent("求导题目需要写出完整推导过程吗，还是只写最终答案即可？");
            studentQ2.setCreatedAt(LocalDateTime.now().minusHours(3));
            msgRepo.save(studentQ2);
        }

        System.out.println("Exam Q&A Test Data Seeded! 登录教师 1001/123456 进入问答中心，或学生 2024001/2024002 查看。");
    }

    private void seedReservationData(UserRepository userRepo, ExamRepository examRepo,
                                     QuestionRepository qRepo, ExamQuestionRepository eqRepo,
                                     ExamTimeSlotRepository slotRepo, ExamReservationRepository resRepo,
                                     PasswordEncoder encoder, User teacher) {
        String reservationExamTitle = "英语四级模拟（预约制）";
        Exam existingReservationExam = examRepo.findAll().stream()
                .filter(e -> reservationExamTitle.equals(e.getTitle()))
                .findFirst().orElse(null);
        if (existingReservationExam != null && resRepo.countByExamId(existingReservationExam.getId()) > 0) {
            return;
        }
        if (teacher == null) {
            return;
        }

        System.out.println("Seeding Reservation Queue Test Data...");

        Exam exam = existingReservationExam;
        if (exam == null) {
            exam = new Exam();
            exam.setTitle(reservationExamTitle);
            exam.setDescription("高并发预约考试测试：需提前预约时段，限时入场。");
            exam.setCourse("英语");
            exam.setDuration(120);
            exam.setState("PUBLISHED");
            exam.setCreator(teacher);
            exam.setStartTime(LocalDateTime.now().plusDays(1));
            exam.setEndTime(LocalDateTime.now().plusDays(14));
            exam.setCoverUrl("/covers/english.png");
            exam.setReservationEnabled(true);
            exam.setMaxConcurrentUsers(5);
            exam.setTimeSlotDuration(60);
            exam.setReservationStartTime(LocalDateTime.now().minusDays(1));
            exam.setReservationEndTime(LocalDateTime.now().plusDays(10));
            exam.setAdmissionTimeout(15);
            exam = examRepo.save(exam);

            List<Question> questions = new ArrayList<>();
            questions.add(createQuestion(qRepo, teacher, "SINGLE", "Choose the correct word: He ___ to school every day.",
                    "[{\"label\":\"A\",\"text\":\"go\"},{\"label\":\"B\",\"text\":\"goes\"},{\"label\":\"C\",\"text\":\"going\"},{\"label\":\"D\",\"text\":\"gone\"}]",
                    "B", "第三人称单数", 5, "语法"));
            questions.add(createQuestion(qRepo, teacher, "SINGLE", "What is the synonym of 'abundant'?",
                    "[{\"label\":\"A\",\"text\":\"scarce\"},{\"label\":\"B\",\"text\":\"plentiful\"},{\"label\":\"C\",\"text\":\"tiny\"},{\"label\":\"D\",\"text\":\"empty\"}]",
                    "B", null, 5, "词汇"));
            linkQuestionsToExam(eqRepo, exam, questions);
        }

        LocalDateTime base = LocalDateTime.now().plusHours(2).withMinute(0).withSecond(0).withNano(0);
        List<ExamTimeSlot> existingSlots = slotRepo.findByExamIdOrderByStartTimeAsc(exam.getId());
        ExamTimeSlot slot1;
        ExamTimeSlot slot2;
        if (existingSlots.size() >= 2) {
            slot1 = existingSlots.get(0);
            slot2 = existingSlots.get(1);
        } else {
            slot1 = createTimeSlot(slotRepo, exam, base, base.plusHours(2), 3);
            slot2 = createTimeSlot(slotRepo, exam, base.plusHours(2), base.plusHours(4), 5);
            createTimeSlot(slotRepo, exam, base.plusHours(4), base.plusHours(6), 10);
        }

        String[][] students = {
                {"2024101", "预约生A", "CONFIRMED", "1"},
                {"2024102", "预约生B", "CONFIRMED", "1"},
                {"2024103", "预约生C", "ADMITTED", "1"},
                {"2024104", "预约生D", "PENDING", null},
                {"2024105", "预约生E", "PENDING", null},
                {"2024106", "预约生F", "CONFIRMED", "2"},
                {"2024107", "预约生G", "ADMITTED", "2"},
        };

        Map<Long, ExamTimeSlot> slotMap = Map.of(slot1.getId(), slot1, slot2.getId(), slot2);
        Map<Long, int[]> slotCounts = new HashMap<>();
        slotMap.keySet().forEach(id -> slotCounts.put(id, new int[2]));

        int pendingPos = 1;
        for (String[] row : students) {
            User student = userRepo.findByUsername(row[0]).orElseGet(() -> {
                User u = new User();
                u.setUsername(row[0]);
                u.setFullName(row[1]);
                u.setPassword(encoder.encode("123456"));
                u.setRole("STUDENT");
                u.setClazz("预约测试班");
                u.setCreatedAt(LocalDateTime.now().minusDays(10));
                return userRepo.save(u);
            });

            if (resRepo.findByExamIdAndStudentId(exam.getId(), student.getId()).isPresent()) {
                continue;
            }

            ExamReservation reservation = new ExamReservation();
            reservation.setExam(exam);
            reservation.setStudent(student);
            reservation.setStatus(row[2]);
            reservation.setCreatedAt(LocalDateTime.now().minusHours(12 - pendingPos));

            if ("PENDING".equals(row[2])) {
                reservation.setQueuePosition(pendingPos++);
            } else {
                ExamTimeSlot slot = "1".equals(row[3]) ? slot1 : slot2;
                reservation.setTimeSlot(slot);
                reservation.setConfirmedAt(LocalDateTime.now().minusMinutes(30));
                reservation.setExpiredAt(LocalDateTime.now().plusMinutes(15));
                int[] counts = slotCounts.get(slot.getId());
                counts[0]++;
                if ("ADMITTED".equals(row[2])) {
                    reservation.setAdmittedAt(LocalDateTime.now().minusMinutes(10));
                    counts[1]++;
                }
            }
            resRepo.save(reservation);
        }

        for (Map.Entry<Long, int[]> entry : slotCounts.entrySet()) {
            ExamTimeSlot slot = slotRepo.findById(entry.getKey()).orElseThrow();
            slot.setReservedCount(entry.getValue()[0]);
            slot.setActiveCount(entry.getValue()[1]);
            slotRepo.save(slot);
        }

        System.out.println("Reservation Queue Test Data Seeded! 登录 2024101-2024107/123456 体验预约排队。");
    }

    private ExamTimeSlot createTimeSlot(ExamTimeSlotRepository repo, Exam exam,
                                        LocalDateTime start, LocalDateTime end, int capacity) {
        ExamTimeSlot slot = new ExamTimeSlot();
        slot.setExam(exam);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(capacity);
        slot.setReservedCount(0);
        slot.setActiveCount(0);
        return repo.save(slot);
    }

    private void seedExamVersionAndReplayData(ExamRepository examRepo, QuestionRepository qRepo,
                                              ExamQuestionRepository eqRepo, SubmissionRepository subRepo,
                                              ExamVersionRepository versionRepo, AnswerSnapshotRepository snapshotRepo,
                                              ExamVersionService versionService, ObjectMapper objectMapper,
                                              User teacher) {
        Exam mathExam = examRepo.findAll().stream()
                .filter(e -> "高等数学期中考试".equals(e.getTitle()))
                .findFirst().orElse(null);
        if (mathExam == null || teacher == null) {
            return;
        }

        if (versionRepo.countByExamId(mathExam.getId()) == 0) {
            System.out.println("Seeding Exam Version Test Data...");
            versionService.createVersion(mathExam.getId(), teacher.getUsername(), "初始发布版本");

            Question extraQ = createQuestion(qRepo, teacher, "SINGLE", "lim(x→0) sin(x)/x 的值是？",
                    "[{\"label\":\"A\",\"text\":\"0\"},{\"label\":\"B\",\"text\":\"1\"},{\"label\":\"C\",\"text\":\"∞\"},{\"label\":\"D\",\"text\":\"不存在\"}]",
                    "B", "重要极限", 10, "极限");
            ExamQuestion eq = new ExamQuestion();
            eq.setExam(mathExam);
            eq.setQuestion(extraQ);
            eq.setScore(10);
            eq.setSequence(eqRepo.findByExamIdOrderBySequenceAsc(mathExam.getId()).size() + 1);
            eqRepo.save(eq);

            versionService.createVersion(mathExam.getId(), teacher.getUsername(), "新增极限计算题");
            System.out.println("Exam Version Test Data Seeded! 在「高等数学期中考试」版本管理中查看。");
        }

        Submission replaySub = subRepo.findAll().stream()
                .filter(s -> "2024999".equals(s.getStudent().getUsername()))
                .filter(s -> s.getExam().getTitle().contains("预警测试"))
                .findFirst().orElse(null);

        if (replaySub != null && snapshotRepo.countBySubmissionId(replaySub.getId()) == 0) {
            System.out.println("Seeding Answer Replay Snapshot Data...");
            List<ExamQuestion> examQuestions = eqRepo.findByExamIdOrderBySequenceAsc(replaySub.getExam().getId());
            if (examQuestions.isEmpty()) {
                return;
            }

            try {
                Map<Long, String> answers = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(3, examQuestions.size()); i++) {
                    Question q = examQuestions.get(i).getQuestion();
                    answers.put(q.getId(), i == 0 ? q.getAnswer() : "WRONG");
                }
                String fullJson = objectMapper.writeValueAsString(answers);

                AnswerSnapshot snap1 = new AnswerSnapshot();
                snap1.setSubmission(replaySub);
                snap1.setTimestamp(replaySub.getStartTime().plusMinutes(5));
                snap1.setElapsedSeconds(300);
                snap1.setCurrentQuestionIndex(0);
                snap1.setTimeLeft(5100);
                snap1.setAnswersDelta(fullJson);
                snap1.setIsFullSnapshot(true);
                snapshotRepo.save(snap1);

                answers.put(examQuestions.get(1).getQuestion().getId(), examQuestions.get(1).getQuestion().getAnswer());
                String deltaJson = objectMapper.writeValueAsString(
                        Map.of(examQuestions.get(1).getQuestion().getId(), examQuestions.get(1).getQuestion().getAnswer()));

                AnswerSnapshot snap2 = new AnswerSnapshot();
                snap2.setSubmission(replaySub);
                snap2.setTimestamp(replaySub.getStartTime().plusMinutes(15));
                snap2.setElapsedSeconds(900);
                snap2.setCurrentQuestionIndex(2);
                snap2.setTimeLeft(4500);
                snap2.setAnswersDelta(deltaJson);
                snap2.setIsFullSnapshot(false);
                snapshotRepo.save(snap2);

                Map<Long, String> finalAnswers = new LinkedHashMap<>();
                for (ExamQuestion eqItem : examQuestions) {
                    finalAnswers.put(eqItem.getQuestion().getId(),
                            eqItem.getQuestion().getKnowledgePoint() != null
                                    && eqItem.getQuestion().getKnowledgePoint().contains("一元二次") ? "WRONG" : eqItem.getQuestion().getAnswer());
                }

                AnswerSnapshot snap3 = new AnswerSnapshot();
                snap3.setSubmission(replaySub);
                snap3.setTimestamp(replaySub.getStartTime().plusMinutes(55));
                snap3.setElapsedSeconds(3300);
                snap3.setCurrentQuestionIndex(examQuestions.size() - 1);
                snap3.setTimeLeft(300);
                snap3.setAnswersDelta(objectMapper.writeValueAsString(finalAnswers));
                snap3.setIsFullSnapshot(true);
                snapshotRepo.save(snap3);

                System.out.println("Answer Replay Test Data Seeded! 教师登录后可在答卷回放中查看 submission #" + replaySub.getId());
            } catch (Exception e) {
                System.out.println("Failed to seed answer snapshots: " + e.getMessage());
            }
        }
    }

    private void seedPkBattleData(UserRepository userRepo, QuestionRepository qRepo,
                                  PkSessionRepository sessionRepo, PkAnswerRepository answerRepo,
                                  ObjectMapper objectMapper) {
        if (sessionRepo.findWeeklyRankedSessions(
                LocalDateTime.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                        .withHour(0).withMinute(0).withSecond(0).withNano(0)).size() >= 3) {
            return;
        }

        System.out.println("Seeding PK Battle History Test Data...");

        List<User> players = List.of("2024001", "2024002", "2022001", "2022002", "2022003").stream()
                .map(u -> userRepo.findByUsername(u).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        if (players.size() < 2) {
            return;
        }

        List<Question> pkQuestions = qRepo.findAll().stream()
                .filter(q -> "SINGLE".equals(q.getType()) || "JUDGE".equals(q.getType()))
                .limit(10)
                .toList();
        if (pkQuestions.size() < 5) {
            return;
        }

        List<Long> questionIds = pkQuestions.stream().map(Question::getId).toList();
        String questionIdsJson;
        try {
            questionIdsJson = objectMapper.writeValueAsString(questionIds);
        } catch (Exception e) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < Math.min(6, players.size() - 1); i++) {
            User p1 = players.get(i);
            User p2 = players.get((i + 1) % players.size());
            boolean p1Wins = i % 2 == 0;

            PkSession session = new PkSession();
            session.setPlayer1(p1);
            session.setPlayer2(p2);
            session.setState("FINISHED");
            session.setQuestionCount(Math.min(5, questionIds.size()));
            session.setCurrentQuestionIndex(5);
            session.setStartTime(now.minusDays(i + 1));
            session.setEndTime(now.minusDays(i + 1).plusMinutes(8));
            session.setPlayer1Score(p1Wins ? 40 : 20);
            session.setPlayer2Score(p1Wins ? 20 : 40);
            session.setWinner(p1Wins ? p1 : p2);
            session.setIsBotGame(false);
            session.setQuestionIds(questionIdsJson);
            session.setCreatedAt(now.minusDays(i + 1));
            session = sessionRepo.save(session);

            for (int q = 0; q < 5; q++) {
                Question question = pkQuestions.get(q);
                PkAnswer a1 = new PkAnswer();
                a1.setSession(session);
                a1.setPlayer(p1);
                a1.setQuestionId(question.getId());
                a1.setQuestionIndex(q);
                a1.setAnswer(question.getAnswer());
                a1.setIsCorrect(q % 2 == 0 || p1Wins);
                a1.setTimeUsed(8 + q);
                a1.setAnsweredAt(session.getStartTime().plusSeconds(q * 30L + 8));
                answerRepo.save(a1);

                PkAnswer a2 = new PkAnswer();
                a2.setSession(session);
                a2.setPlayer(p2);
                a2.setQuestionId(question.getId());
                a2.setQuestionIndex(q);
                a2.setAnswer(p1Wins && q % 2 == 1 ? "X" : question.getAnswer());
                a2.setIsCorrect(!p1Wins || q % 2 == 0);
                a2.setTimeUsed(12 + q);
                a2.setAnsweredAt(session.getStartTime().plusSeconds(q * 30L + 12));
                answerRepo.save(a2);
            }
        }

        System.out.println("PK Battle Test Data Seeded! 登录任意学生账号进入 PK 排行榜查看。");
    }
}
