package com.example.hackathon.hackathon.scheduler;

import com.example.hackathon.hackathon.entity.Application;
import com.example.hackathon.hackathon.repository.ApplicationRepository;
import com.example.hackathon.hackathon.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60000) // Run every 1 minute (60000 ms)
    public void processPendingNotifications() {
        log.info("Starting notification scheduler execution at {}", LocalDateTime.now());

        try {
            // Process SMS notifications (immediate)
            processSmsNotifications();

            // Process 24-hour email notifications
            process24HrEmailNotifications();

            // Process 48-hour email notifications
            process48HrEmailNotifications();

        } catch (Exception e) {
            log.error("Error in notification scheduler execution", e);
        }

        log.info("Completed notification scheduler execution at {}", LocalDateTime.now());
    }

    private void processSmsNotifications() {
        List<Application> pendingSms = applicationRepository.findPendingSmsNotifications();
        log.info("Found {} applications pending SMS notification", pendingSms.size());

        for (Application application : pendingSms) {
            try {
                boolean success = notificationService.sendSmsNotification(application);
                if (success) {
                    log.info("Successfully sent SMS notification for application ID: {}", application.getId());
                } else {
                    log.warn("Failed to send SMS notification for application ID: {}", application.getId());
                }
            } catch (Exception e) {
                log.error("Error processing SMS notification for application ID: {}", application.getId(), e);
            }
        }
    }

    private void process24HrEmailNotifications() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        List<Application> pending24HrEmail = applicationRepository.findPending24HrEmailNotifications(cutoffTime);
        log.info("Found {} applications pending 24-hour email notification", pending24HrEmail.size());

        for (Application application : pending24HrEmail) {
            try {
                boolean success = notificationService.send24HrEmailNotification(application);
                if (success) {
                    log.info("Successfully sent 24-hour email notification for application ID: {}", application.getId());
                } else {
                    log.warn("Failed to send 24-hour email notification for application ID: {}", application.getId());
                }
            } catch (Exception e) {
                log.error("Error processing 24-hour email notification for application ID: {}", application.getId(), e);
            }
        }
    }

    private void process48HrEmailNotifications() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        List<Application> pending48HrEmail = applicationRepository.findPending48HrEmailNotifications(cutoffTime);
        log.info("Found {} applications pending 48-hour email notification", pending48HrEmail.size());

        for (Application application : pending48HrEmail) {
            try {
                boolean success = notificationService.send48HrEmailNotification(application);
                if (success) {
                    log.info("Successfully sent 48-hour email notification for application ID: {}", application.getId());
                } else {
                    log.warn("Failed to send 48-hour email notification for application ID: {}", application.getId());
                }
            } catch (Exception e) {
                log.error("Error processing 48-hour email notification for application ID: {}", application.getId(), e);
            }
        }
    }
}
