package com.notificationsystem;

import com.notificationsystem.client.NotificationHttpClient;
import com.notificationsystem.config.NotificationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TestConfig {

    @Bean
    @Primary
    public NotificationConfig notificationConfig() {
        NotificationConfig config = new NotificationConfig();
        config.setEmailEndpoint("http://localhost:8080/api/email/send");
        config.setSmsEndpoint("http://localhost:8080/api/sms/send");
        config.setMaxRetries(3);
        config.setBaseDelayMs(1000);
        NotificationConfig.Http http = new NotificationConfig.Http();
        http.setConnectTimeout(10000);
        http.setReadTimeout(30000);
        config.setHttp(http);
        return config;
    }

    @Bean
    @Primary
    public NotificationHttpClient notificationHttpClient(ObjectMapper objectMapper, NotificationConfig config) {
        return new NotificationHttpClient(objectMapper, config) {
            @Override
            public EmailResponse sendEmail(String idempotencyKey, String to, String subject, String htmlBody) {
                return new EmailResponse(true, "Mock sent", "mock-email-id");
            }

            @Override
            public SmsResponse sendSms(String idempotencyKey, String to, String message) {
                return new SmsResponse(true, "Mock sent", "mock-sms-id");
            }
        };
    }
}