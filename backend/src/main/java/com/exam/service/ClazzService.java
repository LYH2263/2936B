package com.exam.service;

import com.exam.entity.Clazz;
import com.exam.entity.ClazzStudent;
import com.exam.entity.User;
import com.exam.repository.ClazzRepository;
import com.exam.repository.ClazzStudentRepository;
import com.exam.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClazzService {

    private final ClazzRepository clazzRepository;
    private final ClazzStudentRepository clazzStudentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigService systemConfigService;

    public ClazzService(ClazzRepository clazzRepository, 
                        ClazzStudentRepository clazzStudentRepository,
                        UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        SystemConfigService systemConfigService) {
        this.clazzRepository = clazzRepository;
        this.clazzStudentRepository = clazzStudentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemConfigService = systemConfigService;
    }

    public List<Clazz> getAllClazzes() {
        return clazzRepository.findAll();
    }

    public Page<Clazz> getClazzes(String keyword, String currentUserRole, Long teacherId, Pageable pageable) {
        boolean isTeacher = currentUserRole.contains("TEACHER");
        List<Clazz> allClazzes;
        
        if (isTeacher) {
            allClazzes = clazzRepository.findByTeacherId(teacherId);
        } else {
            allClazzes = clazzRepository.findAll();
        }
        
        if (keyword != null && !keyword.isEmpty()) {
            final String lowerKeyword = keyword.toLowerCase();
            allClazzes = allClazzes.stream()
                    .filter(c -> c.getName().toLowerCase().contains(lowerKeyword)
                            || (c.getGrade() != null && c.getGrade().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allClazzes.size());
        List<Clazz> content = start >= allClazzes.size() ? new ArrayList<>() : allClazzes.subList(start, end);
        
        return new org.springframework.data.domain.PageImpl<>(content, pageable, allClazzes.size());
    }

    public Clazz getClazzById(Long id) {
        return clazzRepository.findById(id).orElseThrow(() -> new RuntimeException("班级不存在"));
    }

    public List<User> getClazzStudents(Long clazzId) {
        return clazzStudentRepository.findStudentsByClazzId(clazzId);
    }

    @Transactional
    public Clazz createClazz(Clazz clazz, String username) {
        if (clazzRepository.existsByName(clazz.getName())) {
            throw new RuntimeException("班级名称已存在");
        }
        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (clazz.getTeacher() == null) {
            clazz.setTeacher(teacher);
        }
        return clazzRepository.save(clazz);
    }

    @Transactional
    public Clazz updateClazz(Long id, Clazz clazzDetails, String currentUserRole, Long teacherId) {
        Clazz clazz = getClazzById(id);
        boolean isTeacher = currentUserRole.contains("TEACHER");
        
        if (isTeacher && !clazz.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("权限不足：只能修改自己管理的班级");
        }
        
        if (clazzDetails.getName() != null && !clazzDetails.getName().equals(clazz.getName())) {
            if (clazzRepository.existsByName(clazzDetails.getName())) {
                throw new RuntimeException("班级名称已存在");
            }
            clazz.setName(clazzDetails.getName());
        }
        
        if (clazzDetails.getGrade() != null) {
            clazz.setGrade(clazzDetails.getGrade());
        }
        if (clazzDetails.getTeacher() != null && !isTeacher) {
            clazz.setTeacher(clazzDetails.getTeacher());
        }
        
        return clazzRepository.save(clazz);
    }

    @Transactional
    public void deleteClazz(Long id, String currentUserRole, Long teacherId) {
        Clazz clazz = getClazzById(id);
        boolean isTeacher = currentUserRole.contains("TEACHER");
        
        if (isTeacher && !clazz.getTeacher().getId().equals(teacherId)) {
            throw new RuntimeException("权限不足：只能删除自己管理的班级");
        }
        
        clazzStudentRepository.deleteByClazzId(id);
        clazzRepository.delete(clazz);
    }

    @Transactional
    public void addStudentToClazz(Long clazzId, Long studentId) {
        Clazz clazz = getClazzById(clazzId);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        if (!"STUDENT".equals(student.getRole())) {
            throw new RuntimeException("只能添加学生角色的用户");
        }
        
        if (clazzStudentRepository.existsByClazzIdAndStudentId(clazzId, studentId)) {
            return;
        }
        
        ClazzStudent cs = new ClazzStudent();
        cs.setClazz(clazz);
        cs.setStudent(student);
        clazzStudentRepository.save(cs);
        
        student.setClazz(clazz.getName());
        userRepository.save(student);
    }

    @Transactional
    public void addStudentsToClazz(Long clazzId, List<Long> studentIds) {
        for (Long studentId : studentIds) {
            addStudentToClazz(clazzId, studentId);
        }
    }

    @Transactional
    public void removeStudentFromClazz(Long clazzId, Long studentId) {
        ClazzStudent cs = clazzStudentRepository.findByClazzIdAndStudentId(clazzId, studentId)
                .orElseThrow(() -> new RuntimeException("该学生不在此班级中"));
        clazzStudentRepository.delete(cs);
        
        User student = userRepository.findById(studentId).orElse(null);
        if (student != null) {
            List<Clazz> remainingClazzes = clazzStudentRepository.findClazzesByStudentId(studentId);
            if (remainingClazzes.isEmpty()) {
                student.setClazz(null);
            } else {
                student.setClazz(remainingClazzes.get(0).getName());
            }
            userRepository.save(student);
        }
    }

    @Transactional
    public Map<String, Object> batchImportStudents(Long clazzId, MultipartFile file) {
        int minLength = systemConfigService.getConfig().getPasswordMinLength() != null ? 
                        systemConfigService.getConfig().getPasswordMinLength() : 6;
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            reader.readLine();
            
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length < 3) {
                    errors.add("行格式错误: " + line);
                    continue;
                }
                
                String username = data[0].trim();
                String fullName = data[1].trim();
                String password = data.length > 2 ? data[2].trim() : "123456";
                
                if (username.isEmpty() || fullName.isEmpty()) {
                    errors.add("用户名或姓名不能为空: " + line);
                    continue;
                }
                
                if (password.length() < minLength) {
                    errors.add("密码长度不足: " + username);
                    continue;
                }
                
                try {
                    User student;
                    if (userRepository.existsByUsername(username)) {
                        student = userRepository.findByUsername(username).get();
                        if (!"STUDENT".equals(student.getRole())) {
                            errors.add("用户不是学生角色: " + username);
                            continue;
                        }
                        skipCount++;
                    } else {
                        student = new User();
                        student.setUsername(username);
                        student.setFullName(fullName);
                        student.setPassword(passwordEncoder.encode(password));
                        student.setRole("STUDENT");
                        student = userRepository.save(student);
                        successCount++;
                    }
                    
                    addStudentToClazz(clazzId, student.getId());
                } catch (Exception e) {
                    errors.add("处理用户 " + username + " 时出错: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("导入失败: " + e.getMessage());
        }
        
        return Map.of(
            "successCount", successCount,
            "skipCount", skipCount,
            "errors", errors
        );
    }

    public List<Clazz> getClazzesByStudentId(Long studentId) {
        return clazzStudentRepository.findClazzesByStudentId(studentId);
    }
}
