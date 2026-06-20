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

            // 4. Seed Grading Workbench Acceptance Test Data
            seedGradingWorkbenchData(userRepository, examRepository, questionRepository,
                    examQuestionRepository, submissionRepository, submissionAnswerRepository,
                    passwordEncoder, teacher1);
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
}
