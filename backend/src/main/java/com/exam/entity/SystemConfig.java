package com.exam.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "system_config")
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sysName;
    private String logoUrl;
    private String footerText;

    // 安全设置 (Security Settings)
    private Integer passwordMinLength;
    private Integer loginMaxFailures;
    private Integer sessionTimeout; // in minutes

    // 考试规则 (Exam Rules)
    private Integer defaultDuration; // in minutes
    private Integer tabSwitchLimit;
    private String defaultAntiCheat; // JSON or comma separated strategies

    // 邮件/短信配置 (Notification Config)
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUser;
    private String smtpPass;
    private String smsApiUrl;
    private String smsApiKey;
}
