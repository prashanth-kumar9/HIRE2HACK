package com.company.screening.infrastructure.repository;

import com.company.screening.domain.Booking;
import com.company.screening.domain.BookingStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface BookingRepository extends MongoRepository<Booking, String> {

    List<Booking> findByStatusAndBookingDateTimeBetween(
            BookingStatus status, Instant from, Instant to);

    List<Booking> findByStatusIn(Collection<BookingStatus> statuses);

    List<Booking> findByStatusInAndBookingDateTimeLessThanEqual(
            Collection<BookingStatus> statuses, Instant maxDateTime);

    List<Booking> findByCandidateId(String candidateId);

    List<Booking> findByApplicationId(String applicationId);
}
