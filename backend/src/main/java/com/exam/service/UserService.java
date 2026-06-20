package com.exam.service;

import com.exam.entity.User;
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
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemConfigService systemConfigService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, SystemConfigService systemConfigService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.systemConfigService = systemConfigService;
    }

    public Page<User> searchUsers(String keyword, String role, String clazz, String currentUserRole, Pageable pageable) {
        boolean skipAdmin = currentUserRole.contains("TEACHER");
        
        if (keyword != null && !keyword.isEmpty()) {
            if (skipAdmin) {
                return userRepository.searchExcludingAdmins(keyword, pageable);
            }
            return userRepository.findByUsernameContainingOrFullNameContaining(keyword, keyword, pageable);
        }
        
        if (skipAdmin) {
            return userRepository.findAllExcludingAdmins(pageable);
        }
        return userRepository.findAll(pageable);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User createUser(User user, String currentUserRole) {
        if ("ADMIN".equals(user.getRole()) && currentUserRole.contains("TEACHER")) {
            throw new RuntimeException("权限不足：老师无法创建管理员账号");
        }
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        int minLength = systemConfigService.getConfig().getPasswordMinLength() != null ? 
                        systemConfigService.getConfig().getPasswordMinLength() : 6;
        String password = user.getPassword() != null ? user.getPassword() : "123456";
        
        if (password.length() < minLength) {
            throw new RuntimeException("密码长度不能少于 " + minLength + " 位");
        }
        
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, User userDetails, String currentUserRole) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if ("ADMIN".equals(user.getRole()) && currentUserRole.contains("TEACHER")) {
            throw new RuntimeException("权限不足：无法修改管理员账号");
        }
        if ("ADMIN".equals(userDetails.getRole()) && currentUserRole.contains("TEACHER")) {
            throw new RuntimeException("权限不足：无法将用户变更为管理员");
        }
        
        user.setFullName(userDetails.getFullName());
        user.setRole(userDetails.getRole());
        user.setClazz(userDetails.getClazz());
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id, String currentUserRole) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        if ("ADMIN".equals(user.getRole()) && currentUserRole.contains("TEACHER")) {
            throw new RuntimeException("权限不足：无法删除管理员账号");
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public void batchImport(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            List<User> users = new ArrayList<>();
            // Skip header
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length >= 4) {
                    User user = new User();
                    user.setUsername(data[0].trim());
                    user.setFullName(data[1].trim());
                    user.setPassword(passwordEncoder.encode(data[2].trim()));
                    user.setRole(data[3].trim());
                    if (data.length > 4) {
                        user.setClazz(data[4].trim());
                    }
                    if (!userRepository.existsByUsername(user.getUsername())) {
                        users.add(user);
                    }
                }
            }
            userRepository.saveAll(users);
        } catch (Exception e) {
            throw new RuntimeException("导入失败: " + e.getMessage());
        }
    }

    public byte[] exportUsers(String currentUserRole) {
        List<User> users;
        if (currentUserRole.contains("TEACHER")) {
            users = userRepository.findAll().stream()
                    .filter(u -> !"ADMIN".equals(u.getRole()))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            users = userRepository.findAll();
        }
        
        StringBuilder sb = new StringBuilder();
        // Add BOM for Excel
        sb.append("\uFEFF");
        sb.append("用户名,姓名,角色,班级\n");
        for (User u : users) {
            sb.append(u.getUsername()).append(",")
              .append(u.getFullName()).append(",")
              .append(u.getRole()).append(",")
              .append(u.getClazz() != null ? u.getClazz() : "").append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, String currentUserRole) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        if ("ADMIN".equals(user.getRole()) && currentUserRole.contains("TEACHER")) {
            throw new RuntimeException("权限不足：无法重置管理员密码");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public User updateProfile(String username, String fullName, String newPassword) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("用户不存在"));
        if (fullName != null && !fullName.isEmpty()) {
            user.setFullName(fullName);
        }
        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        return userRepository.save(user);
    }
}
