package com.example.hackathon.hackathon.integration;

import com.example.hackathon.hackathon.entity.Application;
import com.example.hackathon.hackathon.repository.ApplicationRepository;
import com.example.hackathon.hackathon.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationIntegrationTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private NotificationService notificationService;

    private Application application;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        application = Application.builder()
                .candidateName("Test Candidate")
                .email("test@example.com")
                .phoneNumber("+1234567890")
                .position("Developer")
                .selected(true)
                .selectedAt(LocalDateTime.now())
                .smsSent(false)
                .emailSent24Hrs(false)
                .emailSent48Hrs(false)
                .build();
        application = applicationRepository.save(application);
    }

    @Test
    void testSmsNotificationFlow() {
        assertFalse(application.getSmsSent());

        boolean result = notificationService.sendSmsNotification(application);

        assertTrue(result);

        Optional<Application> updated = applicationRepository.findById(application.getId());
        assertTrue(updated.isPresent());
        assertTrue(updated.get().getSmsSent());
    }

    @Test
    void test24HrEmailNotificationFlow() {
        application.setSelectedAt(LocalDateTime.now().minusHours(25));
        applicationRepository.save(application);

        assertFalse(application.getEmailSent24Hrs());

        boolean result = notificationService.send24HrEmailNotification(application);

        assertTrue(result);

        Optional<Application> updated = applicationRepository.findById(application.getId());
        assertTrue(updated.isPresent());
        assertTrue(updated.get().getEmailSent24Hrs());
    }

    @Test
    void test48HrEmailNotificationFlow() {
        application.setSelectedAt(LocalDateTime.now().minusHours(49));
        applicationRepository.save(application);

        assertFalse(application.getEmailSent48Hrs());

        boolean result = notificationService.send48HrEmailNotification(application);

        assertTrue(result);

        Optional<Application> updated = applicationRepository.findById(application.getId());
        assertTrue(updated.isPresent());
        assertTrue(updated.get().getEmailSent48Hrs());
    }

    @Test
    void testNonSelectedCandidateNotProcessed() {
        application.setSelected(false);
        applicationRepository.save(application);

        boolean smsResult = notificationService.sendSmsNotification(application);
        boolean email24Result = notificationService.send24HrEmailNotification(application);
        boolean email48Result = notificationService.send48HrEmailNotification(application);

        assertTrue(smsResult);
        assertTrue(email24Result);
        assertTrue(email48Result);

        Optional<Application> updated = applicationRepository.findById(application.getId());
        assertTrue(updated.isPresent());
    }
}
