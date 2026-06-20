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
        };
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
        exam.setDuration(90);
        exam.setState("PUBLISHED");
        exam.setCreator(creator);
        exam.setStartTime(LocalDateTime.now().minusDays(1));
        exam.setEndTime(LocalDateTime.now().plusDays(7));
        exam.setCoverUrl("/covers/math.png");
        exam = examRepo.save(exam);

        List<Question> questions = new ArrayList<>();
        questions.add(createQuestion(questionRepo, creator, "SINGLE", "函数 f(x) = x^2 在 x=1 处的导数是？", 
            "[{\"label\":\"A\",\"text\":\"1\"},{\"label\":\"B\",\"text\":\"2\"},{\"label\":\"C\",\"text\":\"3\"},{\"label\":\"D\",\"text\":\"0\"}]", "B", "f'(x)=2x, f'(1)=2", 5));
        questions.add(createQuestion(questionRepo, creator, "JUDGE", "矩阵乘法满足交换律。", 
            null, "FALSE", "矩阵乘法一般不满足交换律 AB != BA", 5));
        questions.add(createQuestion(questionRepo, creator, "MULTI", "下列哪些是三角函数？", 
            "[{\"label\":\"A\",\"text\":\"sin(x)\"},{\"label\":\"B\",\"text\":\"cos(x)\"},{\"label\":\"C\",\"text\":\"log(x)\"},{\"label\":\"D\",\"text\":\"tan(x)\"}]", "ABD", null, 5));

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
        Question q = new Question();
        q.setCreator(creator);
        q.setType(type);
        q.setContent(content);
        q.setOptions(options);
        q.setAnswer(answer);
        q.setAnalysis(analysis);
        q.setDefaultScore(score);
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
}
