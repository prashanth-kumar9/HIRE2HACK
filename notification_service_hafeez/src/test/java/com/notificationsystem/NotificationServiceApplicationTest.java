package com.notificationsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestConfig.class)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "notification.email.endpoint=http://localhost:8080/api/email/send",
    "notification.sms.endpoint=http://localhost:8080/api/sms/send"
})
class NotificationServiceApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the Spring Boot application context loads successfully
    }
}