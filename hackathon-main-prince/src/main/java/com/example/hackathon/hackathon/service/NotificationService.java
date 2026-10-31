package com.example.hackathon.hackathon.service;

import com.example.hackathon.hackathon.entity.Application;
import com.example.hackathon.hackathon.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SmsService smsService;
    private final EmailService emailService;
    private final NotificationTemplateService templateService;
    private final ApplicationRepository applicationRepository;

    @Transactional
    public boolean sendSmsNotification(Application application) {
        try {
            String message = templateService.getSmsTemplate();
            var response = smsService.sendSms(application, message);

            if (response.success()) {
                int updated = applicationRepository.markSmsAsSent(application.getId(), java.time.LocalDateTime.now());
                if (updated > 0) {
                    log.info("SMS flag updated to true for application ID: {}", application.getId());
                    return true;
                } else {
                    log.warn("Failed to update SMS flag for application ID: {}", application.getId());
                    return false;
                }
            } else {
                log.error("SMS sending failed for application ID: {}", application.getId());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending SMS notification for application ID: {}", application.getId(), e);
            return false;
        }
    }

    @Transactional
    public boolean send24HrEmailNotification(Application application) {
        try {
            String subject = templateService.getEmail24HrsSubject();
            String message = templateService.getEmail24HrsBody();
            var response = emailService.sendEmail(application, subject, message);

            if (response.success()) {
                int updated = applicationRepository.markEmail24HrsAsSent(application.getId(), java.time.LocalDateTime.now());
                if (updated > 0) {
                    log.info("24-hour email flag updated to true for application ID: {}", application.getId());
                    return true;
                } else {
                    log.warn("Failed to update 24-hour email flag for application ID: {}", application.getId());
                    return false;
                }
            } else {
                log.error("24-hour email sending failed for application ID: {}", application.getId());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending 24-hour email notification for application ID: {}", application.getId(), e);
            return false;
        }
    }

    @Transactional
    public boolean send48HrEmailNotification(Application application) {
        try {
            String subject = templateService.getEmail48HrsSubject();
            String message = templateService.getEmail48HrsBody();
            var response = emailService.sendEmail(application, subject, message);

            if (response.success()) {
                int updated = applicationRepository.markEmail48HrsAsSent(application.getId(), java.time.LocalDateTime.now());
                if (updated > 0) {
                    log.info("48-hour email flag updated to true for application ID: {}", application.getId());
                    return true;
                } else {
                    log.warn("Failed to update 48-hour email flag for application ID: {}", application.getId());
                    return false;
                }
            } else {
                log.error("48-hour email sending failed for application ID: {}", application.getId());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending 48-hour email notification for application ID: {}", application.getId(), e);
            return false;
        }
    }
}
