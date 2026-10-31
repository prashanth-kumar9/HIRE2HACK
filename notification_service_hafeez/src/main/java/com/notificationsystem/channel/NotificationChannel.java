package com.notificationsystem.channel;

import com.notificationsystem.model.Notification;

public interface NotificationChannel {
    void send(Notification notification);
}