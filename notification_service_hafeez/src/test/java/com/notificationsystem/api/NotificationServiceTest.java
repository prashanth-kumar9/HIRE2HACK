package com.notificationsystem.api;

import com.notificationsystem.model.Notification;
import com.notificationsystem.service.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private NotificationService service;

    @Test
    void sendNotificationDelegatesToDispatcher() {
        Notification notification = new Notification("user123", "Test message");

        service.sendNotification(notification);

        verify(dispatcher).dispatch(notification);
    }
}