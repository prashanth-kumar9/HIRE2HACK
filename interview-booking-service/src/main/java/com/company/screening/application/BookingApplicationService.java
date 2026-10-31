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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingApplicationService {

    private final ApplicationRepository applicationRepository;
    private final BookingRepository bookingRepository;
    private final SlotCapacityAlgorithm capacityAlgorithm;
    private final ScreeningNotificationProducer notificationProducer;

    @Transactional
    public BookingResponse submitPublicBooking(String token, PublicBookingRequest request) {
        Application application = applicationRepository.findByBookingToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Booking link is invalid or does not exist"));

        if (application.getStatus() != ApplicationStatus.INVITED) {
            throw new InvalidBookingStateException("Candidate has already booked or application is not eligible for booking (current status: " + application.getStatus() + ")");
        }

        if (application.getTokenExpiresAt() != null && application.getTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidBookingStateException("This booking invitation link has expired");
        }

        int duration = request.durationMinutes() != null ? request.durationMinutes() : 30;
        int buffer = request.bufferTimeMinutes() != null ? request.bufferTimeMinutes() : 15;
        Instant start = request.bookingDateTime();
        Instant effectiveEnd = start.plusSeconds((duration + buffer) * 60L);

        // 1. Enforce DSA Concurrency Algorithm (Max 10 concurrent active interviews with buffer)
        assertSlotCapacityAvailable(start, effectiveEnd, null);

        // 2. Persist Booking
        Booking booking = Booking.builder()
                .bookingId("BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .candidateId(application.getCandidateId())
                .applicationId(application.getApplicationId())
                .status(BookingStatus.SCHEDULED)
                .bookingDateTime(start)
                .durationMinutes(duration)
                .bufferTimeMinutes(buffer)
                .build();
        booking = bookingRepository.save(booking);

        // 3. Update Application State
        application.setCandidateName(request.candidateName());
        application.setCandidatePhone(request.candidatePhone());
        application.setStatus(ApplicationStatus.BOOKED);
        application.setNudgeStage(NudgeStage.BOOKED);
        applicationRepository.save(application);

        // 4. Publish Event to Kafka: screening.notification.request
        notificationProducer.sendNotification(
                booking.getBookingId(),
                ScreeningNotificationProducer.NotificationType.BOOKING_CONFIRMED,
                application.getCandidateEmail() != null ? application.getCandidateEmail() : application.getCandidatePhone(),
                Map.of(
                        "bookingId", booking.getBookingId(),
                        "candidateName", application.getCandidateName() != null ? application.getCandidateName() : "Candidate",
                        "bookingDateTime", booking.getBookingDateTime().toString(),
                        "applicationId", application.getApplicationId()
                )
        );

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse rescheduleBooking(String bookingId, RescheduleRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Cannot reschedule a booking that is " + booking.getStatus());
        }

        Instant newStart = request.newBookingDateTime();
        Instant newEffectiveEnd = newStart.plusSeconds((booking.getDurationMinutes() + booking.getBufferTimeMinutes()) * 60L);

        // 1. Enforce DSA Concurrency Algorithm (exclude the current booking being rescheduled)
        assertSlotCapacityAvailable(newStart, newEffectiveEnd, booking.getBookingId());

        // 2. Update Booking
        booking.setBookingDateTime(newStart);
        booking.setStatus(BookingStatus.SCHEDULED);
        booking = bookingRepository.save(booking);

        // 3. Notify Candidate via Kafka
        Application app = applicationRepository.findById(booking.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found for booking: " + bookingId));

        notificationProducer.sendNotification(
                booking.getBookingId(),
                ScreeningNotificationProducer.NotificationType.RESCHEDULE_CONFIRMED,
                app.getCandidateEmail() != null ? app.getCandidateEmail() : app.getCandidatePhone(),
                Map.of(
                        "bookingId", booking.getBookingId(),
                        "newBookingDateTime", newStart.toString(),
                        "reason", request.reason()
                )
        );

        return toResponse(booking);
    }

    private void assertSlotCapacityAvailable(Instant start, Instant effectiveEnd, String excludeBookingId) {
        List<Booking> candidates = bookingRepository.findByStatusInAndBookingDateTimeLessThanEqual(
                List.of(BookingStatus.SCHEDULED, BookingStatus.CALL_DISPATCHED, BookingStatus.IN_PROGRESS),
                effectiveEnd
        ).stream()
                .filter(b -> excludeBookingId == null || !excludeBookingId.equals(b.getBookingId()))
                .filter(b -> b.getEffectiveEndTime() != null && b.getEffectiveEndTime().isAfter(start))
                .toList();

        boolean canBook = capacityAlgorithm.canBookSlot(start, effectiveEnd, candidates);
        if (!canBook) {
            throw new SlotCapacityExceededException(
                    "Capacity exceeded: More than 10 simultaneous interviews are scheduled during this time window. Please select another slot."
            );
        }
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.getBookingId(),
                b.getCandidateId(),
                b.getApplicationId(),
                b.getStatus().name(),
                b.getBookingDateTime(),
                b.getDurationMinutes(),
                b.getBufferTimeMinutes()
        );
    }
}
