package com.notificationsystem.channel;

import com.notificationsystem.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("emailNotificationChannel")
public class EmailNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Override
    public void send(Notification notification) {
        log.info("Sending EMAIL to user {}: {}", notification.getUserId(), notification.getMessage());
        // In a real implementation, this would call an email service API
    }
}