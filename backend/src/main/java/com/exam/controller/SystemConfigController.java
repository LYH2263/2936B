package com.exam.controller;

import com.exam.entity.SystemConfig;
import com.exam.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ResponseEntity<SystemConfig> getConfig() {
        return ResponseEntity.ok(systemConfigService.getConfig());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemConfig> updateConfig(@RequestBody SystemConfig config) {
        return ResponseEntity.ok(systemConfigService.updateConfig(config));
    }
}
