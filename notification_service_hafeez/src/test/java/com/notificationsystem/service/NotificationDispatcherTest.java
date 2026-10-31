package com.notificationsystem.service;

import com.notificationsystem.channel.NotificationChannel;
import com.notificationsystem.factory.NotificationChannelFactory;
import com.notificationsystem.model.ChannelType;
import com.notificationsystem.model.Notification;
import com.notificationsystem.model.UserPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private UserPreferenceService preferenceService;

    @Mock
    private NotificationChannelFactory channelFactory;

    @Mock
    private NotificationChannel emailChannel;

    @Mock
    private NotificationChannel smsChannel;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(preferenceService, channelFactory);
    }

    @Test
    void dispatchSendsToAllPreferredChannels() {
        // Given
        Notification notification = new Notification("user123", "Test message");
        UserPreference preference = new UserPreference("user123", Set.of(ChannelType.EMAIL, ChannelType.SMS));

        when(preferenceService.getPreference("user123")).thenReturn(preference);
        when(channelFactory.getChannel(ChannelType.EMAIL)).thenReturn(emailChannel);
        when(channelFactory.getChannel(ChannelType.SMS)).thenReturn(smsChannel);

        // When
        dispatcher.dispatch(notification);

        // Then
        verify(emailChannel).send(notification);
        verify(smsChannel).send(notification);
    }

    @Test
    void dispatchUsesDefaultPreferenceWhenUserNotFound() {
        // Given
        Notification notification = new Notification("unknown", "Test message");
        UserPreference defaultPreference = new UserPreference("unknown", Set.of(ChannelType.EMAIL));

        when(preferenceService.getPreference("unknown")).thenReturn(defaultPreference);
        when(channelFactory.getChannel(ChannelType.EMAIL)).thenReturn(emailChannel);

        // When
        dispatcher.dispatch(notification);

        // Then
        verify(emailChannel).send(notification);
        verify(channelFactory, never()).getChannel(ChannelType.SMS);
    }

    @Test
    void dispatchHandlesChannelFailureGracefully() {
        // Given
        Notification notification = new Notification("user123", "Test message");
        UserPreference preference = new UserPreference("user123", Set.of(ChannelType.EMAIL, ChannelType.SMS));

        when(preferenceService.getPreference("user123")).thenReturn(preference);
        when(channelFactory.getChannel(ChannelType.EMAIL)).thenReturn(emailChannel);
        when(channelFactory.getChannel(ChannelType.SMS)).thenThrow(new RuntimeException("SMS service down"));

        // When
        dispatcher.dispatch(notification);

        // Then - email should still be sent despite SMS failure
        verify(emailChannel).send(notification);
    }
}