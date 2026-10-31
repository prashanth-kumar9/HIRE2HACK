package com.example.hackathon.hackathon.service;

import com.example.hackathon.hackathon.entity.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    public SmsResponse sendSms(Application application, String message) {
        log.info("Sending SMS to candidate: {}", application.getCandidateName());
        log.info("Phone number: {}", application.getPhoneNumber());
        log.info("Message: {}", message);

        // Mock SMS sending - simulate success
        boolean success = true;

        if (success) {
            log.info("SMS sent successfully to {}", application.getPhoneNumber());
            return new SmsResponse(true, "SMS sent successfully");
        } else {
            log.error("Failed to send SMS to {}", application.getPhoneNumber());
            return new SmsResponse(false, "Failed to send SMS");
        }
    }

    public record SmsResponse(boolean success, String message) {
    }
}
