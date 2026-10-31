package com.notificationsystem.api;

import com.notificationsystem.model.Notification;
import com.notificationsystem.service.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AsyncNotificationServiceTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private AsyncNotificationService service;

    @Test
    void sendNotificationReturnsCompletableFuture() {
        Notification notification = new Notification("user123", "Test message");

        CompletableFuture<Void> future = service.sendNotification(notification);

        org.assertj.core.api.Assertions.assertThat(future).isNotNull();
        // Note: The actual async execution is tested via integration tests
        // Here we just verify the method returns a future
    }
}