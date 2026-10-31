package com.company.screening.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record PublicBookingRequest(
        @NotBlank(message = "Candidate name is required")
        String candidateName,

        @NotBlank(message = "Candidate phone is required")
        String candidatePhone,

        @NotNull(message = "Booking date and time is required")
        @Future(message = "Booking date and time must be in the future")
        Instant bookingDateTime,

        @Positive(message = "Duration minutes must be positive")
        Integer durationMinutes,      // Defaults to 30 if null

        @Min(value = 0, message = "Buffer time minutes cannot be negative")
        Integer bufferTimeMinutes     // Defaults to 15 if null
) {}
