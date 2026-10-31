package com.company.screening.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScreeningNotificationProducer {

    public static final String TOPIC = "screening.notification.request";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendNotification(String aggregateId, NotificationType type, String recipient, Map<String, Object> payload) {
        NotificationEvent event = new NotificationEvent(
                aggregateId,
                type,
                recipient,
                payload,
                Instant.now()
        );
        log.info("Sending notification [{}] to [{}] via Kafka topic '{}'", type, recipient, TOPIC);
        kafkaTemplate.send(TOPIC, aggregateId, event);
    }

    public enum NotificationType {
        INITIAL_INVITE_SMS,
        BOOKING_REMINDER_EMAIL,
        BOOKING_CONFIRMED,
        RESCHEDULE_CONFIRMED,
        AGENT_DELAYED_WAIT_BUFFER,
        CALL_MISSED_REBOOKING_LINK,
        CANDIDATE_UNRESPONSIVE,
        RECRUITER_SCREENING_RESULT
    }

    public record NotificationEvent(
            String aggregateId,
            NotificationType type,
            String recipient,
            Map<String, Object> payload,
            Instant timestamp
    ) {}
}
