package com.company.screening.api;

import com.company.screening.api.dto.BookingResponse;
import com.company.screening.api.dto.PublicBookingRequest;
import com.company.screening.api.dto.RescheduleRequest;
import com.company.screening.application.BookingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Booking Endpoints", description = "Public candidate form submission and booking rescheduling")
public class BookingController {

    private final BookingApplicationService bookingService;

    /**
     * POST /booking-link/:token
     * Public form submit (creates Application details + Booking)
     */
    @PostMapping("/booking-link/{token}")
    @Operation(summary = "Submit public candidate booking form", description = "Validates link token, checks <= 10 concurrency with buffer, and schedules the interview")
    @ApiResponse(responseCode = "201", description = "Booking successfully confirmed")
    @ApiResponse(responseCode = "404", description = "Token not found or invalid")
    @ApiResponse(responseCode = "409", description = "Concurrency limit exceeded (>10 simultaneous interviews)")
    public ResponseEntity<BookingResponse> submitBookingForm(
            @PathVariable("token") String token,
            @Valid @RequestBody PublicBookingRequest request) {

        BookingResponse response = bookingService.submitPublicBooking(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PATCH /bookings/:id
     * Reschedule booking
     */
    @PatchMapping("/bookings/{id}")
    @Operation(summary = "Reschedule an existing booking", description = "Updates booking slot after verifying buffer & concurrency limits")
    @ApiResponse(responseCode = "200", description = "Booking rescheduled successfully")
    @ApiResponse(responseCode = "404", description = "Booking ID not found")
    @ApiResponse(responseCode = "409", description = "New slot exceeds 10 simultaneous interview capacity")
    public ResponseEntity<BookingResponse> rescheduleBooking(
            @PathVariable("id") String bookingId,
            @Valid @RequestBody RescheduleRequest request) {

        BookingResponse response = bookingService.rescheduleBooking(bookingId, request);
        return ResponseEntity.ok(response);
    }
}
