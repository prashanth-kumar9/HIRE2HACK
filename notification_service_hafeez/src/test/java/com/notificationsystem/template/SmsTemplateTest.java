package com.notificationsystem.template;

import com.notificationsystem.model.ScreeningNotificationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsTemplateTest {

    private final SmsTemplate template = new SmsTemplate();

    @Test
    void renderIncludesCandidateName() {
        ScreeningNotificationRequest request = createRequest("John Doe");

        String rendered = template.render(request);

        assertThat(rendered).contains("John Doe");
    }

    @Test
    void renderIncludesMessage() {
        ScreeningNotificationRequest request = createRequest("Jane Smith");
        request.getNotificationDetails().setMessage("Screening invitation for you.");

        String rendered = template.render(request);

        assertThat(rendered).contains("Screening invitation for you.");
    }

    @Test
    void renderIncludesBookingUrl() {
        ScreeningNotificationRequest request = createRequest("Bob Wilson");
        request.getNotificationDetails().setBookingUrl("https://example.com/book/123");

        String rendered = template.render(request);

        assertThat(rendered).contains("https://example.com/book/123");
    }

    @Test
    void renderIncludesJobId() {
        ScreeningNotificationRequest request = createRequest("Alice Brown");
        request.setJobId("job-456");

        String rendered = template.render(request);

        assertThat(rendered).contains("job-456");
    }

    @Test
    void getRecipientPhoneReturnsCandidatePhone() {
        ScreeningNotificationRequest request = createRequest("Phone User");
        request.getCandidateDetails().setPhone("+15551234567");

        String phone = template.getRecipientPhone(request);

        assertThat(phone).isEqualTo("+15551234567");
    }

    @Test
    void getRecipientPhoneReturnsNullWhenMissing() {
        ScreeningNotificationRequest request = createRequest("No Phone");
        request.getCandidateDetails().setPhone(null);

        String phone = template.getRecipientPhone(request);

        assertThat(phone).isNull();
    }

    private ScreeningNotificationRequest createRequest(String name) {
        ScreeningNotificationRequest request = new ScreeningNotificationRequest();
        request.setJobId("job-123");
        request.setCandidateId("candidate-123");
        request.setApplicationId("app-123");
        request.setEventType("SCREENING_INVITATION");

        ScreeningNotificationRequest.CandidateDetails details = new ScreeningNotificationRequest.CandidateDetails();
        details.setName(name);
        details.setEmail("test@example.com");
        details.setPhone("+1234567890");
        request.setCandidateDetails(details);

        ScreeningNotificationRequest.NotificationDetails notifDetails = new ScreeningNotificationRequest.NotificationDetails();
        notifDetails.setMessage("Please book your screening call.");
        notifDetails.setBookingUrl("https://example.com/book");
        request.setNotificationDetails(notifDetails);

        return request;
    }
}