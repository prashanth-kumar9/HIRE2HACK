package com.company.screening.api;

import com.company.screening.api.dto.CallResultPayload;
import com.company.screening.common.GlobalExceptionHandler;
import com.company.screening.domain.Application;
import com.company.screening.domain.ApplicationStatus;
import com.company.screening.domain.Booking;
import com.company.screening.domain.BookingStatus;
import com.company.screening.infrastructure.kafka.ScreeningNotificationProducer;
import com.company.screening.infrastructure.repository.ApplicationRepository;
import com.company.screening.infrastructure.repository.BookingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VoiceProviderCallbackControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ScreeningNotificationProducer notificationProducer;

    @InjectMocks
    private VoiceProviderCallbackController controller;

    private ObjectMapper objectMapper;
    private Booking booking;
    private Application application;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();

        booking = Booking.builder()
                .bookingId("BK-100")
                .applicationId("APP-100")
                .candidateId("CAND-100")
                .status(BookingStatus.IN_PROGRESS)
                .bookingDateTime(Instant.now().minusSeconds(1800))
                .build();

        application = Application.builder()
                .applicationId("APP-100")
                .candidateId("CAND-100")
                .candidateName("John Smith")
                .candidateEmail("john@example.com")
                .candidatePhone("+15555555555")
                .recruiterEmail("recruiter@company.com")
                .status(ApplicationStatus.IN_SCREENING)
                .missedAttemptsCount(0)
                .build();
    }

    @Test
    @DisplayName("Should mark SCREENING_PASSED when score >= 70 and call succeeded")
    void shouldHandleSuccessfulCallPassed() throws Exception {
        CallResultPayload payload = new CallResultPayload(
                "BK-100", 85, "Great communication and problem solving skills.", true
        );

        when(bookingRepository.findById("BK-100")).thenReturn(Optional.of(booking));
        when(applicationRepository.findById("APP-100")).thenReturn(Optional.of(application));

        mockMvc.perform(post("/api/v1/screening/call-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getCallCompletedAt()).isNotNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SCREENING_PASSED);
        assertThat(application.getScreeningScore()).isEqualTo(85);

        verify(notificationProducer).sendNotification(
                eq("APP-100"),
                eq(ScreeningNotificationProducer.NotificationType.RECRUITER_SCREENING_RESULT),
                eq("recruiter@company.com"),
                argThat(map -> Boolean.TRUE.equals(map.get("passed")) && Integer.valueOf(85).equals(map.get("score")))
        );
    }

    @Test
    @DisplayName("Should mark SCREENING_FAILED when score < 70 and call succeeded")
    void shouldHandleSuccessfulCallFailed() throws Exception {
        CallResultPayload payload = new CallResultPayload(
                "BK-100", 55, "Needs improvement in technical fundamentals.", true
        );

        when(bookingRepository.findById("BK-100")).thenReturn(Optional.of(booking));
        when(applicationRepository.findById("APP-100")).thenReturn(Optional.of(application));

        mockMvc.perform(post("/api/v1/screening/call-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SCREENING_FAILED);
        assertThat(application.getScreeningScore()).isEqualTo(55);

        verify(notificationProducer).sendNotification(
                eq("APP-100"),
                eq(ScreeningNotificationProducer.NotificationType.RECRUITER_SCREENING_RESULT),
                eq("recruiter@company.com"),
                argThat(map -> Boolean.FALSE.equals(map.get("passed")) && Integer.valueOf(55).equals(map.get("score")))
        );
    }

    @Test
    @DisplayName("Should handle 1st missed call when completedSuccessfully is false")
    void shouldHandleFirstMissedCall() throws Exception {
        CallResultPayload payload = new CallResultPayload(
                "BK-100", 0, "No answer from candidate", false
        );

        when(bookingRepository.findById("BK-100")).thenReturn(Optional.of(booking));
        when(applicationRepository.findById("APP-100")).thenReturn(Optional.of(application));

        mockMvc.perform(post("/api/v1/screening/call-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.MISSED);
        assertThat(application.getMissedAttemptsCount()).isEqualTo(1);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INVITED);
        assertThat(application.getBookingToken()).isNotNull();

        verify(notificationProducer).sendNotification(
                eq("APP-100"),
                eq(ScreeningNotificationProducer.NotificationType.CALL_MISSED_REBOOKING_LINK),
                eq("john@example.com"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Should mark UNRESPONSIVE on 2nd missed call when completedSuccessfully is false")
    void shouldHandleSecondMissedCall() throws Exception {
        application.setMissedAttemptsCount(1); // Already missed once
        CallResultPayload payload = new CallResultPayload(
                "BK-100", 0, "Candidate phone unreachable", false
        );

        when(bookingRepository.findById("BK-100")).thenReturn(Optional.of(booking));
        when(applicationRepository.findById("APP-100")).thenReturn(Optional.of(application));

        mockMvc.perform(post("/api/v1/screening/call-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.MISSED);
        assertThat(application.getMissedAttemptsCount()).isEqualTo(2);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNRESPONSIVE);

        verify(notificationProducer).sendNotification(
                eq("APP-100"),
                eq(ScreeningNotificationProducer.NotificationType.CANDIDATE_UNRESPONSIVE),
                eq("recruiter@company.com"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Should return 404 when booking is not found")
    void shouldReturn404WhenBookingNotFound() throws Exception {
        CallResultPayload payload = new CallResultPayload("BK-NONE", 80, "Good", true);
        when(bookingRepository.findById("BK-NONE")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/screening/call-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }
}
