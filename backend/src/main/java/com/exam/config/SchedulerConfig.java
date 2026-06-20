package com.exam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import com.exam.service.LearningAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    private final LearningAlertService alertService;

    public SchedulerConfig(LearningAlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledLearningAlertScan() {
        log.info("Starting scheduled learning alert scan...");
        try {
            var stats = alertService.runFullScan();
            log.info("Scheduled learning alert scan completed: {}", stats);
        } catch (Exception e) {
            log.error("Scheduled learning alert scan failed", e);
        }
    }
}
