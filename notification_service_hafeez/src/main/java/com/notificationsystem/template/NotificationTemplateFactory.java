package com.notificationsystem.template;

import com.notificationsystem.model.ScreeningNotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateFactory {
    private final EmailTemplate emailTemplate;
    private final SmsTemplate smsTemplate;

    @Autowired
    public NotificationTemplateFactory(EmailTemplate emailTemplate, SmsTemplate smsTemplate) {
        this.emailTemplate = emailTemplate;
        this.smsTemplate = smsTemplate;
    }

    public NotificationTemplate getTemplate(String channel) {
        return switch (channel.toUpperCase()) {
            case "EMAIL" -> emailTemplate;
            case "SMS" -> smsTemplate;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }

    public EmailTemplate getEmailTemplate() {
        return emailTemplate;
    }

    public SmsTemplate getSmsTemplate() {
        return smsTemplate;
    }
}