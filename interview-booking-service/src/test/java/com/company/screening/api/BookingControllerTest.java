package com.company.screening.api;

import com.company.screening.api.dto.BookingResponse;
import com.company.screening.api.dto.PublicBookingRequest;
import com.company.screening.api.dto.RescheduleRequest;
import com.company.screening.application.BookingApplicationService;
import com.company.screening.common.GlobalExceptionHandler;
import com.company.screening.common.exception.InvalidBookingStateException;
import com.company.screening.common.exception.ResourceNotFoundException;
import com.company.screening.common.exception.SlotCapacityExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookingApplicationService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private ObjectMapper objectMapper;
    private Instant futureTime;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        futureTime = Instant.now().plusSeconds(3600);
    }

    @Test
    @DisplayName("POST /booking-link/{token} - Should return 201 when booking is successful")
    void shouldReturn201OnSuccessfulBooking() throws Exception {
        PublicBookingRequest request = new PublicBookingRequest(
                "Alice Smith",
                "+1234567890",
                futureTime,
                30,
                15
        );

        BookingResponse response = new BookingResponse(
                "BK-001",
                "CAND-1",
                "APP-1",
                "SCHEDULED",
                futureTime,
                30,
                15
        );

        when(bookingService.submitPublicBooking(eq("token-valid"), any(PublicBookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/booking-link/token-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value("BK-001"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("POST /booking-link/{token} - Should return 404 when token is not found")
    void shouldReturn404WhenTokenNotFound() throws Exception {
        PublicBookingRequest request = new PublicBookingRequest(
                "Alice Smith",
                "+1234567890",
                futureTime,
                30,
                15
        );

        when(bookingService.submitPublicBooking(eq("token-unknown"), any(PublicBookingRequest.class)))
                .thenThrow(new ResourceNotFoundException("Booking link is invalid"));

        mockMvc.perform(post("/booking-link/token-unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @DisplayName("POST /booking-link/{token} - Should return 409 when capacity is exceeded")
    void shouldReturn409WhenCapacityExceeded() throws Exception {
        PublicBookingRequest request = new PublicBookingRequest(
                "Alice Smith",
                "+1234567890",
                futureTime,
                30,
                15
        );

        when(bookingService.submitPublicBooking(eq("token-full"), any(PublicBookingRequest.class)))
                .thenThrow(new SlotCapacityExceededException("Capacity exceeded"));

        mockMvc.perform(post("/booking-link/token-full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Slot Capacity Exceeded"))
                .andExpect(jsonPath("$.maxAllowedSimultaneousInterviews").value(10));
    }

    @Test
    @DisplayName("POST /booking-link/{token} - Should return 400 when validation fails")
    void shouldReturn400WhenValidationFails() throws Exception {
        // Candidate phone is blank and date is in past
        PublicBookingRequest invalidRequest = new PublicBookingRequest(
                "Alice Smith",
                "",
                Instant.now().minusSeconds(3600),
                -10,
                -5
        );

        mockMvc.perform(post("/booking-link/token-xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /bookings/{id} - Should return 200 on successful reschedule")
    void shouldReturn200OnReschedule() throws Exception {
        RescheduleRequest request = new RescheduleRequest(futureTime.plusSeconds(3600), "Conflict");
        BookingResponse response = new BookingResponse(
                "BK-001",
                "CAND-1",
                "APP-1",
                "SCHEDULED",
                request.newBookingDateTime(),
                30,
                15
        );

        when(bookingService.rescheduleBooking(eq("BK-001"), any(RescheduleRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/bookings/BK-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("BK-001"));
    }

    @Test
    @DisplayName("PATCH /bookings/{id} - Should return 400 when booking is in invalid state")
    void shouldReturn400OnInvalidStateReschedule() throws Exception {
        RescheduleRequest request = new RescheduleRequest(futureTime.plusSeconds(3600), "Conflict");

        when(bookingService.rescheduleBooking(eq("BK-001"), any(RescheduleRequest.class)))
                .thenThrow(new InvalidBookingStateException("Cannot reschedule a booking that is COMPLETED"));

        mockMvc.perform(patch("/bookings/BK-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Booking State"));
    }
}
