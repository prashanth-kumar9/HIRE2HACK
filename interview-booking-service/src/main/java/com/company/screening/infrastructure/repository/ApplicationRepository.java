package com.company.screening.infrastructure.repository;

import com.company.screening.domain.Application;
import com.company.screening.domain.ApplicationStatus;
import com.company.screening.domain.NudgeStage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends MongoRepository<Application, String> {

    Optional<Application> findByBookingToken(String bookingToken);

    List<Application> findByStatusAndNudgeStage(ApplicationStatus status, NudgeStage nudgeStage);

    @Query("{ 'status': ?0, 'nudgeStage': ?1, 'lastNudgeSentAt': { '$lte': ?2 } }")
    List<Application> findPendingEmailNudges(ApplicationStatus status, NudgeStage nudgeStage, Instant thresholdTime);
}
