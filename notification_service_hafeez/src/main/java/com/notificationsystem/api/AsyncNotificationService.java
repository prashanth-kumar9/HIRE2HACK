package com.notificationsystem.api;

import com.notificationsystem.model.Notification;
import com.notificationsystem.service.NotificationDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncNotificationService {
    private final NotificationDispatcher dispatcher;

    @Autowired
    public AsyncNotificationService(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Async("notificationTaskExecutor")
    public CompletableFuture<Void> sendNotification(Notification notification) {
        dispatcher.dispatch(notification);
        return CompletableFuture.completedFuture(null);
    }
}