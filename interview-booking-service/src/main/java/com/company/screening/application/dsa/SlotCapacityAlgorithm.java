package com.company.screening.application.dsa;

import com.company.screening.domain.Booking;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SlotCapacityAlgorithm {

    public static final int MAX_CONCURRENT_INTERVIEWS = 10;

    /**
     * Represents a point-in-time event for the sweep-line algorithm.
     * type: +1 indicates an interview starting, -1 indicates an interview (including buffer) ending.
     */
    public record IntervalEvent(Instant time, int type) {}

    /**
     * Determines whether a new interview slot can be booked without exceeding the
     * concurrency limit of 10 simultaneous active interviews at any instant.
     *
     * @param requestedStart       Start time of the proposed slot
     * @param requestedEffectiveEnd End time of the proposed slot including buffer
     * @param overlappingBookings  Currently scheduled or active bookings that overlap with this window
     * @return true if concurrency remains <= 10 at all times, false if limit would be breached
     */
    public boolean canBookSlot(Instant requestedStart, Instant requestedEffectiveEnd, List<Booking> overlappingBookings) {
        if (requestedStart == null || requestedEffectiveEnd == null) {
            throw new IllegalArgumentException("Start and end times must not be null");
        }
        if (!requestedEffectiveEnd.isAfter(requestedStart)) {
            throw new IllegalArgumentException("Effective end time must be strictly after start time");
        }

        List<IntervalEvent> events = new ArrayList<>();

        // Add requested slot interval
        events.add(new IntervalEvent(requestedStart, +1));
        events.add(new IntervalEvent(requestedEffectiveEnd, -1));

        // Add all existing active bookings (using effective end time with buffer)
        if (overlappingBookings != null) {
            for (Booking booking : overlappingBookings) {
                if (booking != null && booking.getBookingDateTime() != null && booking.getEffectiveEndTime() != null) {
                    events.add(new IntervalEvent(booking.getBookingDateTime(), +1));
                    events.add(new IntervalEvent(booking.getEffectiveEndTime(), -1));
                }
            }
        }

        // Sort events chronologically.
        // If timestamps are equal, end events (-1) come before start events (+1)
        // so that a meeting ending right as another begins does not double-count concurrency.
        events.sort(Comparator.comparing(IntervalEvent::time)
                .thenComparingInt(IntervalEvent::type));

        int currentActive = 0;
        for (IntervalEvent event : events) {
            currentActive += event.type();
            if (currentActive > MAX_CONCURRENT_INTERVIEWS) {
                return false;
            }
        }

        return true;
    }
}
