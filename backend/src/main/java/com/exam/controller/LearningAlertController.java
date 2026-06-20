package com.exam.controller;

import com.exam.entity.LearningAlert;
import com.exam.entity.User;
import com.exam.repository.UserRepository;
import com.exam.service.LearningAlertQueryService;
import com.exam.service.LearningAlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning-alerts")
public class LearningAlertController {

    private final LearningAlertService alertService;
    private final LearningAlertQueryService queryService;
    private final UserRepository userRepository;

    public LearningAlertController(LearningAlertService alertService,
                                    LearningAlertQueryService queryService,
                                    UserRepository userRepository) {
        this.alertService = alertService;
        this.queryService = queryService;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan(@RequestBody(required = false) Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime start = LocalDateTime.now();
        Map<String, Integer> stats;

        if (params != null && params.get("studentId") != null) {
            Long studentId = Long.valueOf(params.get("studentId").toString());
            stats = alertService.scanStudent(studentId);
            result.put("scope", "student");
            result.put("studentId", studentId);
        } else {
            stats = alertService.runFullScan();
            result.put("scope", "all");
        }

        result.put("stats", stats);
        result.put("startedAt", start.toString());
        result.put("finishedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAlerts(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean isResolved,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        Page<LearningAlert> pageData = queryService.getAlerts(studentId, alertType, severity, isResolved, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageData.getContent().stream()
                .map(queryService::enrichAlert)
                .collect(Collectors.toList()));
        result.put("total", pageData.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", pageData.getTotalPages());
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(queryService.getAlertStats());
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAlertDetail(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getAlertDetail(id));
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> markResolved(@PathVariable Long id, Principal principal) {
        queryService.markAsResolved(id, principal);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("id", id);
        return ResponseEntity.ok(r);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @PostMapping("/resolve/batch")
    public ResponseEntity<Map<String, Object>> markBatchResolved(@RequestBody Map<String, List<Long>> body, Principal principal) {
        List<Long> ids = body.get("ids");
        int count = queryService.markBatchAsResolved(ids, principal);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("resolvedCount", count);
        return ResponseEntity.ok(r);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean isResolved) {

        byte[] data = queryService.exportToExcel(studentId, alertType, severity, isResolved);
        String filename = "learning-alerts-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", encoded);
        headers.setContentLength(data.length);
        headers.add("Access-Control-Expose-Headers", "Content-Disposition");

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyAlerts(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<LearningAlert> alerts;
        if ("STUDENT".equals(user.getRole())) {
            alerts = queryService.getStudentOwnAlerts(user.getId());
        } else {
            alerts = List.of();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("list", alerts.stream()
                .map(queryService::enrichAlert)
                .collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }
}
