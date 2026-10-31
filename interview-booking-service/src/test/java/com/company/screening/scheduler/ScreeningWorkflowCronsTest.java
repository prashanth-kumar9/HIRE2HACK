package com.company.screening.scheduler;

import com.company.screening.domain.Application;
import com.company.screening.domain.ApplicationStatus;
import com.company.screening.domain.Booking;
import com.company.screening.domain.BookingStatus;
import com.company.screening.domain.NudgeStage;
import com.company.screening.infrastructure.kafka.ScreeningNotificationProducer;
import com.company.screening.infrastructure.repository.ApplicationRepository;
import com.company.screening.infrastructure.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScreeningWorkflowCronsTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ScreeningNotificationProducer notificationProducer;

    @InjectMocks
    private ScreeningWorkflowCrons crons;

    private Application application;
    private Booking booking;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .applicationId("APP-1")
                .candidateId("CAND-1")
                .candidateName("John")
                .candidatePhone("+1234567890")
                .candidateEmail("john@example.com")
                .bookingToken("tok-1")
                .status(ApplicationStatus.INVITED)
                .nudgeStage(NudgeStage.NONE)
                .missedAttemptsCount(0)
                .build();

        booking = Booking.builder()
                .bookingId("BK-1")
                .applicationId("APP-1")
                .candidateId("CAND-1")
                .status(BookingStatus.SCHEDULED)
                .bookingDateTime(Instant.now().minusSeconds(10))
                .durationMinutes(30)
                .bufferTimeMinutes(15)
                .build();
    }

    @Test
    @DisplayName("processUnbookedNudges - Should send initial SMS and 24h follow-up email")
    void shouldProcessUnbookedNudges() {
        Application unnudgedApp = application;
        Application reminderApp = Application.builder()
                .applicationId("APP-2")
                .candidateName("Sarah")
                .candidateEmail("sarah@example.com")
                .candidatePhone("+1987654321")
                .bookingToken("tok-2")
                .status(ApplicationStatus.INVITED)
                .nudgeStage(NudgeStage.SMS_SENT)
                .lastNudgeSentAt(Instant.now().minusSeconds(90000))
                .build();

        when(applicationRepository.findByStatusAndNudgeStage(ApplicationStatus.INVITED, NudgeStage.NONE))
                .thenReturn(List.of(unnudgedApp));
        when(applicationRepository.findPendingEmailNudges(eq(ApplicationStatus.INVITED), eq(NudgeStage.SMS_SENT), any(Instant.class)))
                .thenReturn(List.of(reminderApp));

        crons.processUnbookedNudges();

        assertThat(unnudgedApp.getNudgeStage()).isEqualTo(NudgeStage.SMS_SENT);
        assertThat(unnudgedApp.getLastNudgeSentAt()).isNotNull();

        assertThat(reminderApp.getNudgeStage()).isEqualTo(NudgeStage.EMAIL_NUDGE_SENT);

        verify(notificationProducer).sendNotification(
                eq("APP-1"),
                eq(ScreeningNotificationProducer.NotificationType.INITIAL_INVITE_SMS),
                eq("+1234567890"),
                anyMap()
        );

        verify(notificationProducer).sendNotification(
                eq("APP-2"),
                eq(ScreeningNotificationProducer.NotificationType.BOOKING_REMINDER_EMAIL),
                eq("sarah@example.com"),
                anyMap()
        );
    }

    @Test
    @DisplayName("dispatchScheduledCalls - Should set CALL_DISPATCHED and application IN_SCREENING")
    void shouldDispatchScheduledCalls() {
        when(bookingRepository.findByStatusAndBookingDateTimeBetween(eq(BookingStatus.SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(booking));
        when(applicationRepository.findById("APP-1")).thenReturn(Optional.of(application));

        crons.dispatchScheduledCalls();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CALL_DISPATCHED);
        assertThat(booking.getCallInitiatedAt()).isNotNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.IN_SCREENING);

        verify(bookingRepository).save(booking);
        verify(applicationRepository).save(application);
        verify(notificationProducer).sendNotification(
                eq("BK-1"),
                eq(ScreeningNotificationProducer.NotificationType.BOOKING_CONFIRMED),
                eq("+1234567890"),
                argThat(map -> "INITIATE_VOICE_CALL".equals(map.get("action")))
        );
    }

    @Test
    @DisplayName("handleBufferTimeoutsAndMissedCalls - Should send AGENT_DELAYED_WAIT_BUFFER only once")
    void shouldSendAgentDelayNotificationOnce() {
        // Scheduled 4 minutes ago, still CALL_DISPATCHED, within 45-min buffer window
        booking.setStatus(BookingStatus.CALL_DISPATCHED);
        booking.setBookingDateTime(Instant.now().minusSeconds(240)); // 4 mins ago

        when(bookingRepository.findByStatusIn(anyCollection())).thenReturn(List.of(booking));
        when(applicationRepository.findById("APP-1")).thenReturn(Optional.of(application));

        // First run: should send notification
        crons.handleBufferTimeoutsAndMissedCalls();

        assertThat(booking.isAgentDelayNotified()).isTrue();
        verify(notificationProducer, times(1)).sendNotification(
                eq("BK-1"),
                eq(ScreeningNotificationProducer.NotificationType.AGENT_DELAYED_WAIT_BUFFER),
                eq("+1234567890"),
                anyMap()
        );

        // Second run: agentDelayNotified is already true -> should NOT send again!
        crons.handleBufferTimeoutsAndMissedCalls();

        verify(notificationProducer, times(1)).sendNotification(
                eq("BK-1"),
                eq(ScreeningNotificationProducer.NotificationType.AGENT_DELAYED_WAIT_BUFFER),
                anyString(),
                anyMap()
        );
    }

    @Test
    @DisplayName("handleBufferTimeoutsAndMissedCalls - Should handle first miss when buffer expires")
    void shouldHandleFirstMissWhenBufferExpires() {
        // Scheduled 50 minutes ago -> effective end (45 min) has passed
        booking.setStatus(BookingStatus.CALL_DISPATCHED);
        booking.setBookingDateTime(Instant.now().minusSeconds(3000));

        when(bookingRepository.findByStatusIn(anyCollection())).thenReturn(List.of(booking));
        when(applicationRepository.findById("APP-1")).thenReturn(Optional.of(application));

        crons.handleBufferTimeoutsAndMissedCalls();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.MISSED);
        assertThat(application.getMissedAttemptsCount()).isEqualTo(1);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INVITED);
        assertThat(application.getBookingToken()).isNotNull();

        // Must NOT send AGENT_DELAYED_WAIT_BUFFER
        verify(notificationProducer, never()).sendNotification(
                anyString(),
                eq(ScreeningNotificationProducer.NotificationType.AGENT_DELAYED_WAIT_BUFFER),
                anyString(),
                anyMap()
        );

        verify(notificationProducer).sendNotification(
                eq("APP-1"),
                eq(ScreeningNotificationProducer.NotificationType.CALL_MISSED_REBOOKING_LINK),
                eq("john@example.com"),
                anyMap()
        );
    }

    @Test
    @DisplayName("handleBufferTimeoutsAndMissedCalls - Should mark UNRESPONSIVE on second miss")
    void shouldHandleSecondMissWhenBufferExpires() {
        application.setMissedAttemptsCount(1); // Already missed once
        booking.setStatus(BookingStatus.CALL_DISPATCHED);
        booking.setBookingDateTime(Instant.now().minusSeconds(3000));

        when(bookingRepository.findByStatusIn(anyCollection())).thenReturn(List.of(booking));
        when(applicationRepository.findById("APP-1")).thenReturn(Optional.of(application));

        crons.handleBufferTimeoutsAndMissedCalls();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.MISSED);
        assertThat(application.getMissedAttemptsCount()).isEqualTo(2);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNRESPONSIVE);

        verify(notificationProducer).sendNotification(
                eq("APP-1"),
                eq(ScreeningNotificationProducer.NotificationType.CANDIDATE_UNRESPONSIVE),
                eq("recruiter@company.com"),
                anyMap()
        );
    }
}
