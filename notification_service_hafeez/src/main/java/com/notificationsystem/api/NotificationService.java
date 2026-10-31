package com.notificationsystem.api;

import com.notificationsystem.model.Notification;
import com.notificationsystem.service.NotificationDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationDispatcher dispatcher;

    @Autowired
    public NotificationService(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void sendNotification(Notification notification) {
        dispatcher.dispatch(notification);
    }
}