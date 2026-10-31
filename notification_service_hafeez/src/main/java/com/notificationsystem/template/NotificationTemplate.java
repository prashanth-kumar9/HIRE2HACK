package com.notificationsystem.template;

import com.notificationsystem.model.ScreeningNotificationRequest;

public interface NotificationTemplate {
    String render(ScreeningNotificationRequest request);
}