package com.example.hackathon.hackathon.service;

import com.example.hackathon.hackathon.entity.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public EmailResponse sendEmail(Application application, String subject, String message) {
        log.info("Sending email to candidate: {}", application.getCandidateName());
        log.info("Email address: {}", application.getEmail());
        log.info("Subject: {}", subject);
        log.info("Message: {}", message);

        // Mock email sending - simulate success
        boolean success = true;

        if (success) {
            log.info("Email sent successfully to {}", application.getEmail());
            return new EmailResponse(true, "Email sent successfully");
        } else {
            log.error("Failed to send email to {}", application.getEmail());
            return new EmailResponse(false, "Failed to send email");
        }
    }

    public record EmailResponse(boolean success, String message) {
    }
}
