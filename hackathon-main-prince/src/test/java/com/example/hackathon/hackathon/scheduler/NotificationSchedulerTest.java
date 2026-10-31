package com.example.hackathon.hackathon.scheduler;

import com.example.hackathon.hackathon.entity.Application;
import com.example.hackathon.hackathon.repository.ApplicationRepository;
import com.example.hackathon.hackathon.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationScheduler notificationScheduler;

    private Application application;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .id(1L)
                .candidateName("John Doe")
                .email("john@example.com")
                .phoneNumber("+1234567890")
                .position("Software Engineer")
                .selected(true)
                .selectedAt(LocalDateTime.now())
                .smsSent(false)
                .emailSent24Hrs(false)
                .emailSent48Hrs(false)
                .build();
    }

    @Test
    void processPendingNotifications_ProcessesSmsNotifications() {
        when(applicationRepository.findPendingSmsNotifications())
                .thenReturn(Arrays.asList(application));
        when(notificationService.sendSmsNotification(any(Application.class)))
                .thenReturn(true);

        notificationScheduler.processPendingNotifications();

        verify(applicationRepository, times(1)).findPendingSmsNotifications();
        verify(notificationService, times(1)).sendSmsNotification(application);
    }

    @Test
    void processPendingNotifications_Processes24HrEmailNotifications() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(25);
        application.setSelectedAt(cutoffTime);

        when(applicationRepository.findPendingSmsNotifications())
                .thenReturn(Collections.emptyList());
        when(applicationRepository.findPending24HrEmailNotifications(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(application));
        when(notificationService.send24HrEmailNotification(any(Application.class)))
                .thenReturn(true);

        notificationScheduler.processPendingNotifications();

        verify(applicationRepository, times(1)).findPending24HrEmailNotifications(any(LocalDateTime.class));
        verify(notificationService, times(1)).send24HrEmailNotification(application);
    }

    @Test
    void processPendingNotifications_Processes48HrEmailNotifications() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(49);
        application.setSelectedAt(cutoffTime);

        when(applicationRepository.findPendingSmsNotifications())
                .thenReturn(Collections.emptyList());
        when(applicationRepository.findPending24HrEmailNotifications(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(applicationRepository.findPending48HrEmailNotifications(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(application));
        when(notificationService.send48HrEmailNotification(any(Application.class)))
                .thenReturn(true);

        notificationScheduler.processPendingNotifications();

        verify(applicationRepository, times(1)).findPending48HrEmailNotifications(any(LocalDateTime.class));
        verify(notificationService, times(1)).send48HrEmailNotification(application);
    }

    @Test
    void processPendingNotifications_DoesNotProcessNonSelectedCandidates() {
        application.setSelected(false);

        when(applicationRepository.findPendingSmsNotifications())
                .thenReturn(Collections.emptyList());
        when(applicationRepository.findPending24HrEmailNotifications(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(applicationRepository.findPending48HrEmailNotifications(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        notificationScheduler.processPendingNotifications();

        verify(notificationService, never()).sendSmsNotification(any());
        verify(notificationService, never()).send24HrEmailNotification(any());
        verify(notificationService, never()).send48HrEmailNotification(any());
    }

    @Test
    void processPendingNotifications_ContinuesOnSingleFailure() {
        Application app1 = Application.builder()
                .id(1L)
                .selected(true)
                .smsSent(false)
                .build();
        Application app2 = Application.builder()
                .id(2L)
                .selected(true)
                .smsSent(false)
                .build();

        when(applicationRepository.findPendingSmsNotifications())
                .thenReturn(Arrays.asList(app1, app2));
        when(notificationService.sendSmsNotification(app1))
                .thenReturn(false);
        when(notificationService.sendSmsNotification(app2))
                .thenReturn(true);

        notificationScheduler.processPendingNotifications();

        verify(notificationService, times(1)).sendSmsNotification(app1);
        verify(notificationService, times(1)).sendSmsNotification(app2);
    }

    @Test
    void processPendingNotifications_ProcessesAllPendingNotificationsAfter50Hours() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(50);
        application.setSelectedAt(cutoffTime);

        when(applicationRepository.findPendingSmsNotifications())
                .thenReturn(Arrays.asList(application));
        when(applicationRepository.findPending24HrEmailNotifications(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(application));
        when(applicationRepository.findPending48HrEmailNotifications(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(application));
        when(notificationService.sendSmsNotification(any(Application.class)))
                .thenReturn(true);
        when(notificationService.send24HrEmailNotification(any(Application.class)))
                .thenReturn(true);
        when(notificationService.send48HrEmailNotification(any(Application.class)))
                .thenReturn(true);

        notificationScheduler.processPendingNotifications();

        verify(notificationService, times(1)).sendSmsNotification(application);
        verify(notificationService, times(1)).send24HrEmailNotification(application);
        verify(notificationService, times(1)).send48HrEmailNotification(application);
    }
}
