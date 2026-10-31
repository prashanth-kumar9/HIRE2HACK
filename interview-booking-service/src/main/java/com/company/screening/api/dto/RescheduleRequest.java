package com.company.screening.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleRequest(
        @NotNull(message = "New booking date time is required")
        @Future(message = "New booking date time must be in the future")
        Instant newBookingDateTime,

        @NotBlank(message = "Reason for rescheduling is required")
        String reason
) {}
