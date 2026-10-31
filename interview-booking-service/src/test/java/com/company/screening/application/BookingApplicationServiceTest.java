package com.company.screening.application;

import com.company.screening.api.dto.BookingResponse;
import com.company.screening.api.dto.PublicBookingRequest;
import com.company.screening.api.dto.RescheduleRequest;
import com.company.screening.application.dsa.SlotCapacityAlgorithm;
import com.company.screening.common.exception.InvalidBookingStateException;
import com.company.screening.common.exception.ResourceNotFoundException;
import com.company.screening.common.exception.SlotCapacityExceededException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotCapacityAlgorithm capacityAlgorithm;

    @Mock
    private ScreeningNotificationProducer notificationProducer;

    @InjectMocks
    private BookingApplicationService bookingService;

    private Application testApp;
    private Instant futureTime;

    @BeforeEach
    void setUp() {
        futureTime = Instant.now().plusSeconds(3600);
        testApp = Application.builder()
                .applicationId("APP-123")
                .candidateId("CAND-456")
                .candidateEmail("cand@test.com")
                .candidatePhone("+1234567890")
                .bookingToken("token-xyz")
                .status(ApplicationStatus.INVITED)
                .tokenExpiresAt(futureTime.plusSeconds(86400))
                .build();
    }

    @Test
    @DisplayName("Should successfully book slot when token is valid and capacity allows")
    void shouldSuccessfullyBookSlot() {
        PublicBookingRequest request = new PublicBookingRequest(
                "Jane Doe",
                "+1987654321",
                futureTime,
                30,
                15
        );

        when(applicationRepository.findByBookingToken("token-xyz")).thenReturn(Optional.of(testApp));
        when(bookingRepository.findByStatusInAndBookingDateTimeLessThanEqual(anyCollection(), any(Instant.class))).thenReturn(List.of());
        when(capacityAlgorithm.canBookSlot(any(Instant.class), any(Instant.class), anyList())).thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.submitPublicBooking("token-xyz", request);

        assertThat(response).isNotNull();
        assertThat(response.candidateId()).isEqualTo("CAND-456");
        assertThat(response.applicationId()).isEqualTo("APP-123");
        assertThat(response.status()).isEqualTo("SCHEDULED");
        assertThat(response.durationMinutes()).isEqualTo(30);
        assertThat(response.bufferTimeMinutes()).isEqualTo(15);

        assertThat(testApp.getStatus()).isEqualTo(ApplicationStatus.BOOKED);
        assertThat(testApp.getNudgeStage()).isEqualTo(NudgeStage.BOOKED);
        assertThat(testApp.getCandidateName()).isEqualTo("Jane Doe");

        verify(notificationProducer).sendNotification(
                anyString(),
                eq(ScreeningNotificationProducer.NotificationType.BOOKING_CONFIRMED),
                eq("cand@test.com"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when token does not exist")
    void shouldThrowWhenTokenNotFound() {
        PublicBookingRequest request = new PublicBookingRequest(
                "Jane Doe", "+1987654321", futureTime, 30, 15);
        when(applicationRepository.findByBookingToken("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.submitPublicBooking("invalid-token", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking link is invalid");
    }

    @Test
    @DisplayName("Should throw InvalidBookingStateException when token has expired")
    void shouldThrowWhenTokenExpired() {
        testApp.setTokenExpiresAt(Instant.now().minusSeconds(10));
        when(applicationRepository.findByBookingToken("token-xyz")).thenReturn(Optional.of(testApp));

        PublicBookingRequest request = new PublicBookingRequest(
                "Jane Doe", "+1987654321", futureTime, 30, 15);

        assertThatThrownBy(() -> bookingService.submitPublicBooking("token-xyz", request))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should throw InvalidBookingStateException when application is already booked")
    void shouldThrowWhenAlreadyBooked() {
        testApp.setStatus(ApplicationStatus.BOOKED);
        when(applicationRepository.findByBookingToken("token-xyz")).thenReturn(Optional.of(testApp));

        PublicBookingRequest request = new PublicBookingRequest(
                "Jane Doe", "+1987654321", futureTime, 30, 15);

        assertThatThrownBy(() -> bookingService.submitPublicBooking("token-xyz", request))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    @DisplayName("Should throw SlotCapacityExceededException when algorithm rejects capacity")
    void shouldThrowWhenCapacityExceeded() {
        PublicBookingRequest request = new PublicBookingRequest(
                "Jane Doe", "+1987654321", futureTime, 30, 15);

        when(applicationRepository.findByBookingToken("token-xyz")).thenReturn(Optional.of(testApp));
        when(bookingRepository.findByStatusInAndBookingDateTimeLessThanEqual(anyCollection(), any(Instant.class))).thenReturn(List.of());
        when(capacityAlgorithm.canBookSlot(any(Instant.class), any(Instant.class), anyList())).thenReturn(false);

        assertThatThrownBy(() -> bookingService.submitPublicBooking("token-xyz", request))
                .isInstanceOf(SlotCapacityExceededException.class)
                .hasMessageContaining("Capacity exceeded");
    }

    @Test
    @DisplayName("Should successfully reschedule booking and exclude current booking from capacity check")
    void shouldSuccessfullyReschedule() {
        Booking existingBooking = Booking.builder()
                .bookingId("BK-123")
                .candidateId("CAND-456")
                .applicationId("APP-123")
                .status(BookingStatus.SCHEDULED)
                .bookingDateTime(futureTime)
                .durationMinutes(30)
                .bufferTimeMinutes(15)
                .build();

        Instant newSlot = futureTime.plusSeconds(7200);
        RescheduleRequest request = new RescheduleRequest(newSlot, "Scheduling conflict");

        when(bookingRepository.findById("BK-123")).thenReturn(Optional.of(existingBooking));
        // Return existingBooking as candidate from repository query to verify it gets filtered out
        when(bookingRepository.findByStatusInAndBookingDateTimeLessThanEqual(anyCollection(), any(Instant.class))).thenReturn(List.of(existingBooking));
        when(capacityAlgorithm.canBookSlot(eq(newSlot), any(Instant.class), eq(List.of()))).thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationRepository.findById("APP-123")).thenReturn(Optional.of(testApp));

        BookingResponse response = bookingService.rescheduleBooking("BK-123", request);

        assertThat(response).isNotNull();
        assertThat(response.bookingDateTime()).isEqualTo(newSlot);
        assertThat(existingBooking.getBookingDateTime()).isEqualTo(newSlot);

        verify(notificationProducer).sendNotification(
                eq("BK-123"),
                eq(ScreeningNotificationProducer.NotificationType.RESCHEDULE_CONFIRMED),
                eq("cand@test.com"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Should throw InvalidBookingStateException when attempting to reschedule completed booking")
    void shouldThrowWhenReschedulingCompletedBooking() {
        Booking completedBooking = Booking.builder()
                .bookingId("BK-123")
                .status(BookingStatus.COMPLETED)
                .build();

        when(bookingRepository.findById("BK-123")).thenReturn(Optional.of(completedBooking));

        RescheduleRequest request = new RescheduleRequest(futureTime.plusSeconds(3600), "Changed mind");

        assertThatThrownBy(() -> bookingService.rescheduleBooking("BK-123", request))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("Cannot reschedule");
    }
}
