package com.exam.controller;

import com.exam.entity.User;
import com.exam.repository.UserRepository;
import com.exam.util.JwtUtils;
import com.exam.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final com.exam.service.UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final SystemConfigService systemConfigService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository, 
                          com.exam.service.UserService userService, PasswordEncoder passwordEncoder, 
                          JwtUtils jwtUtils, SystemConfigService systemConfigService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.systemConfigService = systemConfigService;
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody java.util.Map<String, String> request, 
                                          org.springframework.security.core.Authentication authentication) {
        String username = authentication.getName();
        User user = userService.updateProfile(username, request.get("fullName"), request.get("password"));
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("role", user.getRole());
        response.put("fullName", user.getFullName());
        response.put("clazz", user.getClazz());
        response.put("createdAt", user.getCreatedAt());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.get("username"), loginRequest.get("password")));
    
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateToken(loginRequest.get("username"));
    
            User user = userRepository.findByUsername(loginRequest.get("username")).orElseThrow();
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("role", user.getRole());
            response.put("fullName", user.getFullName());
            response.put("clazz", user.getClazz());
            response.put("createdAt", user.getCreatedAt());
    
            return ResponseEntity.ok(response);
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "账号或密码错误");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            e.printStackTrace(); // Added for debugging
            Map<String, String> error = new HashMap<>();
            error.put("message", "登录失败：" + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        int minLength = systemConfigService.getConfig().getPasswordMinLength() != null ? 
                        systemConfigService.getConfig().getPasswordMinLength() : 6;
        if (signUpRequest.getPassword() == null || signUpRequest.getPassword().length() < minLength) {
            return ResponseEntity.badRequest().body("密码长度不能少于 " + minLength + " 位");
        }

        // Create new user's account
        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setRole(signUpRequest.getRole());
        user.setFullName(signUpRequest.getFullName());
        user.setClazz(signUpRequest.getClazz());

        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }
}
