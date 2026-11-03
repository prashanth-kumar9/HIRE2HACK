package com.company.screening.api;

import com.company.screening.api.dto.CallResultPayload;
import com.company.screening.common.exception.ResourceNotFoundException;
import com.company.screening.domain.Application;
import com.company.screening.domain.ApplicationStatus;
import com.company.screening.domain.Booking;
import com.company.screening.domain.BookingStatus;
import com.company.screening.domain.NudgeStage;
import com.company.screening.infrastructure.kafka.ScreeningNotificationProducer;
import com.company.screening.infrastructure.repository.ApplicationRepository;
import com.company.screening.infrastructure.repository.BookingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/screening")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Voice Provider Webhook", description = "Call completion, scoring, and recruiter notification")
public class VoiceProviderCallbackController {

    private final BookingRepository bookingRepository;
    private final ApplicationRepository applicationRepository;
    private final ScreeningNotificationProducer notificationProducer;

    @PostMapping("/call-results")
    @Operation(summary = "Submit call results from voice provider", description = "Updates candidate application with score and alerts recruiter")
    @Transactional
    public ResponseEntity<Void> handleCallResults(@Valid @RequestBody CallResultPayload payload) {
        Booking booking = bookingRepository.findById(payload.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + payload.bookingId()));

        Application application = applicationRepository.findById(booking.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + booking.getApplicationId()));

        if (!payload.completedSuccessfully()) {
            booking.setStatus(BookingStatus.MISSED);
            booking.setCallCompletedAt(Instant.now());
            bookingRepository.save(booking);

            int attempts = application.getMissedAttemptsCount() + 1;
            application.setMissedAttemptsCount(attempts);

            if (attempts == 1) {
                String newToken = UUID.randomUUID().toString();
                application.setBookingToken(newToken);
                application.setTokenExpiresAt(Instant.now().plus(Duration.ofDays(2)));
                application.setStatus(ApplicationStatus.INVITED);
                application.setNudgeStage(NudgeStage.SMS_SENT);
                application.setLastNudgeSentAt(Instant.now());
                applicationRepository.save(application);

                log.warn("Candidate {} missed call. Sending rebooking link with token {}", application.getCandidateName(), newToken);
                notificationProducer.sendNotification(
                        application.getApplicationId(),
                        ScreeningNotificationProducer.NotificationType.CALL_MISSED_REBOOKING_LINK,
                        application.getCandidateEmail() != null ? application.getCandidateEmail() : application.getCandidatePhone(),
                        Map.of(
                                "rebookingUrl", "https://company.com/booking-link/" + newToken,
                                "candidateName", application.getCandidateName() != null ? application.getCandidateName() : "Candidate"
                        )
                );
            } else {
                application.setStatus(ApplicationStatus.UNRESPONSIVE);
                applicationRepository.save(application);

                log.warn("Candidate {} missed second call. Marked as UNRESPONSIVE.", application.getCandidateName());
                notificationProducer.sendNotification(
                        application.getApplicationId(),
                        ScreeningNotificationProducer.NotificationType.CANDIDATE_UNRESPONSIVE,
                        application.getRecruiterEmail() != null ? application.getRecruiterEmail() : "recruiter@company.com",
                        Map.of(
                                "candidateName", application.getCandidateName() != null ? application.getCandidateName() : "Candidate",
                                "applicationId", application.getApplicationId(),
                                "status", "UNRESPONSIVE"
                        )
                );

                notifyApplicationService(application.getApplicationId(), "UNRESPONSIVE", "Unresponsive", null, null);
            }
            return ResponseEntity.ok().build();
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCallCompletedAt(Instant.now());
        bookingRepository.save(booking);

        boolean passed = payload.score() >= 70;
        application.setScreeningScore(payload.score());
        application.setStatus(passed ? ApplicationStatus.SCREENING_PASSED : ApplicationStatus.SCREENING_FAILED);
        application.setScreeningFeedback(payload.transcriptSummary());
        applicationRepository.save(application);

        log.info("Interview completed for candidate {} (App ID: {}). Score: {}, Passed: {}",
                application.getCandidateName(), application.getApplicationId(), payload.score(), passed);

        // Notify Recruiter via Kafka: screening.notification.request
        notificationProducer.sendNotification(
                application.getApplicationId(),
                ScreeningNotificationProducer.NotificationType.RECRUITER_SCREENING_RESULT,
                application.getRecruiterEmail() != null ? application.getRecruiterEmail() : "recruiter@company.com",
                Map.of(
                        "candidateName", application.getCandidateName() != null ? application.getCandidateName() : "Candidate",
                        "score", payload.score(),
                        "passed", passed,
                        "status", application.getStatus().name(),
                        "completedAt", booking.getCallCompletedAt().toString()
                )
        );

        notifyApplicationService(application.getApplicationId(),
                passed ? "SCREENING_PASSED" : "SCREENING_FAILED",
                passed ? "Passed Screening" : "Failed Screening",
                payload.score(),
                payload.transcriptSummary());

        return ResponseEntity.ok().build();
    }

    private void notifyApplicationService(String applicationId, String candidateStatus, String appStatus, Integer score, String feedback) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String appServiceUrl = System.getenv().getOrDefault("APPLICATION_SERVICE_URL", "http://localhost:3002");
            String apiToken = System.getenv().getOrDefault("API_ACCESS_TOKEN", "hire2hack-recruiter-demo-api-token-2026-v1");

            StringBuilder json = new StringBuilder("{");
            json.append("\"candidateStatus\":\"").append(candidateStatus).append("\",");
            json.append("\"applicationStatus\":\"").append(appStatus).append("\"");
            if (score != null) {
                json.append(",\"score\":").append(score);
            }
            if (feedback != null) {
                json.append(",\"feedback\":\"").append(feedback.replace("\"", "\\\"").replace("\n", " ")).append("\"");
            }
            json.append("}");

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(appServiceUrl + "/applications/" + applicationId))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiToken)
                    .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(json.toString()))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .build();
            client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }
}
