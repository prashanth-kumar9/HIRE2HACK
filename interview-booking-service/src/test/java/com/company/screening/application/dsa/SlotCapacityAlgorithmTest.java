package com.company.screening.application.dsa;

import com.company.screening.domain.Booking;
import com.company.screening.domain.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCapacityAlgorithmTest {

    private SlotCapacityAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new SlotCapacityAlgorithm();
    }

    @Test
    @DisplayName("Should permit booking when active concurrent slots are less than 10")
    void shouldPermitBookingWhenUnderCapacity() {
        Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");
        Instant requestedStart = baseTime;
        Instant requestedEndWithBuffer = baseTime.plusSeconds(45 * 60); // 30 min + 15 min buffer

        // Create 9 existing concurrent bookings
        List<Booking> existing = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            existing.add(Booking.builder()
                    .bookingId("BK-" + i)
                    .bookingDateTime(baseTime)
                    .durationMinutes(30)
                    .bufferTimeMinutes(15)
                    .status(BookingStatus.SCHEDULED)
                    .build());
        }

        boolean canBook = algorithm.canBookSlot(requestedStart, requestedEndWithBuffer, existing);
        assertThat(canBook).isTrue(); // 9 + 1 = 10 <= 10 (Allowed)
    }

    @Test
    @DisplayName("Should reject booking when adding it causes concurrency to exceed 10")
    void shouldRejectBookingWhenExceedingCapacity() {
        Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");
        Instant requestedStart = baseTime;
        Instant requestedEndWithBuffer = baseTime.plusSeconds(45 * 60);

        // Create 10 existing concurrent bookings
        List<Booking> existing = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            existing.add(Booking.builder()
                    .bookingId("BK-" + i)
                    .bookingDateTime(baseTime)
                    .durationMinutes(30)
                    .bufferTimeMinutes(15)
                    .status(BookingStatus.SCHEDULED)
                    .build());
        }

        boolean canBook = algorithm.canBookSlot(requestedStart, requestedEndWithBuffer, existing);
        assertThat(canBook).isFalse(); // 10 + 1 = 11 > 10 (Rejected)
    }

    @Test
    @DisplayName("Should account for buffer time: meeting ended but buffer is still active")
    void shouldAccountForBufferTime() {
        Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");
        // 10 meetings from 10:00 to 10:30 with 15 min buffer (effective end: 10:45)
        List<Booking> existing = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            existing.add(Booking.builder()
                    .bookingId("BK-" + i)
                    .bookingDateTime(baseTime)
                    .durationMinutes(30)
                    .bufferTimeMinutes(15)
                    .status(BookingStatus.SCHEDULED)
                    .build());
        }

        // New slot starts at 10:35 (during buffer period 10:30 - 10:45)
        Instant requestedStart = baseTime.plusSeconds(35 * 60);
        Instant requestedEnd = requestedStart.plusSeconds(45 * 60);

        boolean canBook = algorithm.canBookSlot(requestedStart, requestedEnd, existing);
        assertThat(canBook).isFalse(); // Buffer is active, so still 10 + 1 = 11!
    }

    @Test
    @DisplayName("Should permit booking after previous meeting buffer has fully expired")
    void shouldPermitBookingAfterBufferExpires() {
        Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");
        // 10 meetings from 10:00 to 10:30 with 15 min buffer (effective end: 10:45)
        List<Booking> existing = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            existing.add(Booking.builder()
                    .bookingId("BK-" + i)
                    .bookingDateTime(baseTime)
                    .durationMinutes(30)
                    .bufferTimeMinutes(15)
                    .status(BookingStatus.SCHEDULED)
                    .build());
        }

        // New slot starts exactly at 10:45 (after buffer expired)
        Instant requestedStart = baseTime.plusSeconds(45 * 60);
        Instant requestedEnd = requestedStart.plusSeconds(45 * 60);

        boolean canBook = algorithm.canBookSlot(requestedStart, requestedEnd, existing);
        assertThat(canBook).isTrue();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when start or end time is null")
    void shouldThrowWhenTimesAreNull() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                algorithm.canBookSlot(null, Instant.now(), null));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                algorithm.canBookSlot(Instant.now(), null, null));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when effective end is not after start")
    void shouldThrowWhenEndNotAfterStart() {
        Instant now = Instant.now();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                algorithm.canBookSlot(now, now, null));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                algorithm.canBookSlot(now, now.minusSeconds(60), null));
    }

    @Test
    @DisplayName("Should allow booking when overlapping list is null or empty")
    void shouldAllowBookingWithEmptyList() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(1800);

        assertThat(algorithm.canBookSlot(start, end, null)).isTrue();
        assertThat(algorithm.canBookSlot(start, end, List.of())).isTrue();
    }
}
