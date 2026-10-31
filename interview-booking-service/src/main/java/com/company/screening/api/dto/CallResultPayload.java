package com.company.screening.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CallResultPayload(
        @NotBlank(message = "Booking ID is required")
        String bookingId,

        @NotNull(message = "Score is required")
        @Min(0)
        @Max(100)
        Integer score,

        String transcriptSummary,

        boolean completedSuccessfully
) {}
