package com.notificationsystem.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreeningNotificationRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getIdempotencyKeyUsesCorrelationIdWhenPresent() {
        ScreeningNotificationRequest request = new ScreeningNotificationRequest();
        request.setJobId("job-123");
        request.setCandidateId("candidate-123");
        request.setEventType("SCREENING_INVITATION");

        ScreeningNotificationRequest.Metadata metadata = new ScreeningNotificationRequest.Metadata();
        metadata.setCorrelationId("corr-456");
        request.setMetadata(metadata);

        String key = request.getIdempotencyKey();

        assertThat(key).isEqualTo("corr-456");
    }

    @Test
    void getIdempotencyKeyFallsBackToCompositeWhenNoCorrelationId() {
        ScreeningNotificationRequest request = new ScreeningNotificationRequest();
        request.setJobId("job-123");
        request.setCandidateId("candidate-123");
        request.setEventType("SCREENING_INVITATION");

        String key = request.getIdempotencyKey();

        assertThat(key).isEqualTo("job-123-candidate-123-SCREENING_INVITATION");
    }

    @Test
    void jsonDeserializationWorks() throws Exception {
        String json = """
            {
                "job_id": "job-123",
                "candidate_id": "candidate-123",
                "application_id": "app-123",
                "event_type": "SCREENING_INVITATION",
                "candidate_details": {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+1234567890"
                },
                "notification_preference": {
                    "email": true,
                    "sms": true,
                    "whatsapp": false,
                    "voice": false
                },
                "notification_details": {
                    "channel": "EMAIL",
                    "template_id": "template-1",
                    "message": "Please book your screening",
                    "booking_url": "https://example.com/book"
                },
                "metadata": {
                    "correlation_id": "corr-123",
                    "event_timestamp": "2024-01-01T10:00:00Z"
                }
            }
            """;

        ScreeningNotificationRequest request = objectMapper.readValue(json, ScreeningNotificationRequest.class);

        assertThat(request.getJobId()).isEqualTo("job-123");
        assertThat(request.getCandidateId()).isEqualTo("candidate-123");
        assertThat(request.getCandidateDetails().getName()).isEqualTo("John Doe");
        assertThat(request.getNotificationPreference().isEmail()).isTrue();
        assertThat(request.getNotificationPreference().isSms()).isTrue();
        assertThat(request.getMetadata().getCorrelationId()).isEqualTo("corr-123");
    }
}