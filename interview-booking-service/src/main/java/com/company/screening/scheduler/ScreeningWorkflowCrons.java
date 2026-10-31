package com.company.screening.scheduler;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScreeningWorkflowCrons {

    private final ApplicationRepository applicationRepository;
    private final BookingRepository bookingRepository;
    private final ScreeningNotificationProducer notificationProducer;

    /**
     * CRON 1: Process Unbooked Candidate Nudges
     * - Initial SMS: Candidates invited who haven't received SMS yet.
     * - 24-Hour Email Follow-up: Candidates sent SMS >= 24h ago who still haven't booked.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void processUnbookedNudges() {
        Instant now = Instant.now();

        // 1. Initial SMS Invite
        List<Application> unnudged = applicationRepository.findByStatusAndNudgeStage(
                ApplicationStatus.INVITED, NudgeStage.NONE);

        for (Application app : unnudged) {
            log.info("Sending initial SMS invite to candidate: {}", app.getCandidatePhone());
            notificationProducer.sendNotification(
                    app.getApplicationId(),
                    ScreeningNotificationProducer.NotificationType.INITIAL_INVITE_SMS,
                    app.getCandidatePhone(),
                    Map.of(
                            "bookingUrl", "https://company.com/booking-link/" + app.getBookingToken(),
                            "candidateName", app.getCandidateName() != null ? app.getCandidateName() : "Candidate"
                    )
            );
            app.setNudgeStage(NudgeStage.SMS_SENT);
            app.setLastNudgeSentAt(now);
            applicationRepository.save(app);
        }

        // 2. 24-Hour Email Follow-up (e.g. Tuesday 9:05 AM if SMS went Monday 9:05 AM)
        Instant twentyFourHoursAgo = now.minus(Duration.ofHours(24));
        List<Application> pendingEmail = applicationRepository.findPendingEmailNudges(
                ApplicationStatus.INVITED, NudgeStage.SMS_SENT, twentyFourHoursAgo);

        for (Application app : pendingEmail) {
            String recipient = app.getCandidateEmail() != null ? app.getCandidateEmail() : app.getCandidatePhone();
            if (recipient == null) continue;

            log.info("Sending 24h follow-up email reminder to: {}", recipient);
            notificationProducer.sendNotification(
                    app.getApplicationId(),
                    ScreeningNotificationProducer.NotificationType.BOOKING_REMINDER_EMAIL,
                    recipient,
                    Map.of(
                            "bookingUrl", "https://company.com/booking-link/" + app.getBookingToken(),
                            "candidateName", app.getCandidateName() != null ? app.getCandidateName() : "Candidate"
                    )
            );
            app.setNudgeStage(NudgeStage.EMAIL_NUDGE_SENT);
            app.setLastNudgeSentAt(now);
            applicationRepository.save(app);
        }
    }

    /**
     * CRON 2: Trigger Voice Provider at Booked Interview Time
     * Runs every minute to initiate outgoing calls for slots due right now.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void dispatchScheduledCalls() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(Duration.ofMinutes(1));

        List<Booking> dueBookings = bookingRepository.findByStatusAndBookingDateTimeBetween(
                BookingStatus.SCHEDULED, now.minusSeconds(60), windowEnd);

        for (Booking booking : dueBookings) {
            Application app = applicationRepository.findById(booking.getApplicationId()).orElse(null);
            if (app == null) continue;

            log.info("Dispatching voice call for booking {} to phone {}", booking.getBookingId(), app.getCandidatePhone());
            booking.setStatus(BookingStatus.CALL_DISPATCHED);
            booking.setCallInitiatedAt(now);
            bookingRepository.save(booking);

            app.setStatus(ApplicationStatus.IN_SCREENING);
            applicationRepository.save(app);

            notificationProducer.sendNotification(
                    booking.getBookingId(),
                    ScreeningNotificationProducer.NotificationType.BOOKING_CONFIRMED,
                    app.getCandidatePhone() != null ? app.getCandidatePhone() : app.getCandidateEmail(),
                    Map.of(
                            "action", "INITIATE_VOICE_CALL",
                            "bookingId", booking.getBookingId(),
                            "phone", app.getCandidatePhone() != null ? app.getCandidatePhone() : "",
                            "candidateName", app.getCandidateName() != null ? app.getCandidateName() : "Candidate"
                    )
            );
        }
    }

    /**
     * CRON 3: Monitor Buffer Time, Agent Delays & Missed Calls
     * Runs every minute:
     * - If agent is delayed > 3 mins: notify user once to wait for buffer time.
     * - If buffer time expires with no call completed:
     *     - 1st Miss: Send rebooking link via Kafka.
     *     - 2nd Miss: Mark candidate UNRESPONSIVE and notify recruiter.
     */
    @Scheduled(cron = "30 * * * * *")
    @Transactional
    public void handleBufferTimeoutsAndMissedCalls() {
        Instant now = Instant.now();

        List<Booking> activeCalls = bookingRepository.findByStatusIn(
                List.of(BookingStatus.CALL_DISPATCHED, BookingStatus.IN_PROGRESS));

        for (Booking booking : activeCalls) {
            Application app = applicationRepository.findById(booking.getApplicationId()).orElse(null);
            if (app == null) continue;

            Instant slotStart = booking.getBookingDateTime();
            Instant effectiveEnd = booking.getEffectiveEndTime();

            // 1. Buffer Window Expired without completion -> Candidate Missed Call
            if (effectiveEnd != null && now.isAfter(effectiveEnd)) {
                booking.setStatus(BookingStatus.MISSED);
                bookingRepository.save(booking);

                int attempts = app.getMissedAttemptsCount() + 1;
                app.setMissedAttemptsCount(attempts);

                if (attempts == 1) {
                    // First Miss: Generate new rebooking token and send link
                    String newToken = UUID.randomUUID().toString();
                    app.setBookingToken(newToken);
                    app.setTokenExpiresAt(now.plus(Duration.ofDays(2)));
                    app.setStatus(ApplicationStatus.INVITED);
                    app.setNudgeStage(NudgeStage.SMS_SENT);
                    app.setLastNudgeSentAt(now);
                    applicationRepository.save(app);

                    log.warn("Candidate {} missed first call. Sending rebooking link with token {}", app.getCandidateName(), newToken);
                    notificationProducer.sendNotification(
                            app.getApplicationId(),
                            ScreeningNotificationProducer.NotificationType.CALL_MISSED_REBOOKING_LINK,
                            app.getCandidateEmail() != null ? app.getCandidateEmail() : app.getCandidatePhone(),
                            Map.of(
                                    "rebookingUrl", "https://company.com/booking-link/" + newToken,
                                    "candidateName", app.getCandidateName() != null ? app.getCandidateName() : "Candidate"
                            )
                    );
                } else {
                    // Second Miss: Mark UNRESPONSIVE
                    app.setStatus(ApplicationStatus.UNRESPONSIVE);
                    applicationRepository.save(app);

                    log.warn("Candidate {} missed second call. Marked as UNRESPONSIVE.", app.getCandidateName());
                    notificationProducer.sendNotification(
                            app.getApplicationId(),
                            ScreeningNotificationProducer.NotificationType.CANDIDATE_UNRESPONSIVE,
                            app.getRecruiterEmail() != null ? app.getRecruiterEmail() : "recruiter@company.com",
                            Map.of(
                                    "candidateName", app.getCandidateName() != null ? app.getCandidateName() : "Candidate",
                                    "applicationId", app.getApplicationId(),
                                    "status", "UNRESPONSIVE"
                            )
                    );
                }
            } else if (booking.getStatus() == BookingStatus.CALL_DISPATCHED
                    && !booking.isAgentDelayNotified()
                    && slotStart != null
                    && now.isAfter(slotStart.plusSeconds(180))) {
                // 2. Agent Delay Check: If 3 minutes past start and still CALL_DISPATCHED, inform candidate ONCE
                booking.setAgentDelayNotified(true);
                bookingRepository.save(booking);

                notificationProducer.sendNotification(
                        booking.getBookingId(),
                        ScreeningNotificationProducer.NotificationType.AGENT_DELAYED_WAIT_BUFFER,
                        app.getCandidatePhone() != null ? app.getCandidatePhone() : app.getCandidateEmail(),
                        Map.of("message", "Our interviewer agent is briefly delayed. Please stay on line during the 15-minute buffer window.")
                );
            }
        }
    }
}
