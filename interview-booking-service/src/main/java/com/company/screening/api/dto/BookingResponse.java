package com.company.screening.api.dto;

import java.time.Instant;

public record BookingResponse(
        String bookingId,
        String candidateId,
        String applicationId,
        String status,
        Instant bookingDateTime,
        int durationMinutes,
        int bufferTimeMinutes
) {}
