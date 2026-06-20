package com.exam.service;

import com.exam.entity.SystemConfig;
import com.exam.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    public SystemConfig getConfig() {
        List<SystemConfig> configs = systemConfigRepository.findAll();
        if (configs.isEmpty()) {
            SystemConfig defaultConfig = new SystemConfig();
            defaultConfig.setSysName("在线考试系统");
            defaultConfig.setFooterText("Copyright © 2026 在线考试系统");
            
            // Security defaults
            defaultConfig.setPasswordMinLength(6);
            defaultConfig.setLoginMaxFailures(5);
            defaultConfig.setSessionTimeout(30);
            
            // Exam defaults
            defaultConfig.setDefaultDuration(60);
            defaultConfig.setTabSwitchLimit(3);
            
            return systemConfigRepository.save(defaultConfig);
        }
        return configs.get(0);
    }

    @Transactional
    public SystemConfig updateConfig(SystemConfig newConfig) {
        SystemConfig current = getConfig();
        current.setSysName(newConfig.getSysName());
        current.setLogoUrl(newConfig.getLogoUrl());
        current.setFooterText(newConfig.getFooterText());
        
        current.setPasswordMinLength(newConfig.getPasswordMinLength());
        current.setLoginMaxFailures(newConfig.getLoginMaxFailures());
        current.setSessionTimeout(newConfig.getSessionTimeout());
        
        current.setDefaultDuration(newConfig.getDefaultDuration());
        current.setTabSwitchLimit(newConfig.getTabSwitchLimit());
        current.setDefaultAntiCheat(newConfig.getDefaultAntiCheat());
        
        current.setSmtpHost(newConfig.getSmtpHost());
        current.setSmtpPort(newConfig.getSmtpPort());
        current.setSmtpUser(newConfig.getSmtpUser());
        current.setSmtpPass(newConfig.getSmtpPass());
        current.setSmsApiUrl(newConfig.getSmsApiUrl());
        current.setSmsApiKey(newConfig.getSmsApiKey());
        
        return systemConfigRepository.save(current);
    }
}
