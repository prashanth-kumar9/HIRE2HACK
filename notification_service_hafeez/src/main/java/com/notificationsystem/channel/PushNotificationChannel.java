package com.notificationsystem.channel;

import com.notificationsystem.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("pushNotificationChannel")
public class PushNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationChannel.class);

    @Override
    public void send(Notification notification) {
        log.info("Sending PUSH to user {}: {}", notification.getUserId(), notification.getMessage());
        // In a real implementation, this would call a push notification service API
    }
}