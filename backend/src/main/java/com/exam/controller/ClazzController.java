package com.exam.controller;

import com.exam.entity.Clazz;
import com.exam.entity.User;
import com.exam.service.ClazzService;
import com.exam.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clazzes")
@PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
public class ClazzController {

    private final ClazzService clazzService;
    private final UserRepository userRepository;

    public ClazzController(ClazzService clazzService, UserRepository userRepository) {
        this.clazzService = clazzService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Page<Clazz> getClazzes(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String currentUserRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("");
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElse(null);
        Long teacherId = currentUser != null ? currentUser.getId() : null;
        return clazzService.getClazzes(keyword, currentUserRole, teacherId, PageRequest.of(page, size));
    }

    @GetMapping("/all")
    public List<Clazz> getAllClazzes() {
        return clazzService.getAllClazzes();
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public List<Clazz> getMyClazzes(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return List.of();
        }
        return clazzService.getClazzesByStudentId(user.getId());
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER') or hasRole('STUDENT')")
    public List<Clazz> getStudentClazzes(@PathVariable Long studentId) {
        return clazzService.getClazzesByStudentId(studentId);
    }

    @GetMapping("/{id}")
    public Clazz getClazzById(@PathVariable Long id) {
        return clazzService.getClazzById(id);
    }

    @GetMapping("/{id}/students")
    public List<User> getClazzStudents(@PathVariable Long id) {
        return clazzService.getClazzStudents(id);
    }

    @PostMapping
    public ResponseEntity<Clazz> createClazz(@RequestBody Clazz clazz, Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(clazzService.createClazz(clazz, username));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Clazz> updateClazz(@PathVariable Long id, @RequestBody Clazz clazz, Authentication authentication) {
        String currentUserRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("");
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElse(null);
        Long teacherId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(clazzService.updateClazz(id, clazz, currentUserRole, teacherId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClazz(@PathVariable Long id, Authentication authentication) {
        String currentUserRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("");
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElse(null);
        Long teacherId = currentUser != null ? currentUser.getId() : null;
        clazzService.deleteClazz(id, currentUserRole, teacherId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/students")
    public ResponseEntity<?> addStudentsToClazz(@PathVariable Long id, @RequestBody Map<String, List<Long>> request) {
        List<Long> studentIds = request.get("studentIds");
        clazzService.addStudentsToClazz(id, studentIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/students/{studentId}")
    public ResponseEntity<?> addStudentToClazz(@PathVariable Long id, @PathVariable Long studentId) {
        clazzService.addStudentToClazz(id, studentId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/students/{studentId}")
    public ResponseEntity<?> removeStudentFromClazz(@PathVariable Long id, @PathVariable Long studentId) {
        clazzService.removeStudentFromClazz(id, studentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/import")
    public ResponseEntity<Map<String, Object>> importStudents(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = clazzService.batchImportStudents(id, file);
        return ResponseEntity.ok(result);
    }
}
