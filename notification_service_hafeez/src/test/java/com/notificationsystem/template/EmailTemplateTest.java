package com.notificationsystem.template;

import com.notificationsystem.model.ScreeningNotificationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateTest {

    private final EmailTemplate template = new EmailTemplate();

    @Test
    void renderIncludesCandidateName() {
        ScreeningNotificationRequest request = createRequest("John Doe");

        String rendered = template.render(request);

        assertThat(rendered).contains("John Doe");
    }

    @Test
    void renderIncludesMessage() {
        ScreeningNotificationRequest request = createRequest("Jane Smith");
        request.getNotificationDetails().setMessage("You have been invited for a screening call.");

        String rendered = template.render(request);

        assertThat(rendered).contains("You have been invited for a screening call.");
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
    void getSubjectIncludesCandidateNameAndJobId() {
        ScreeningNotificationRequest request = createRequest("Test User");
        request.setJobId("job-789");

        String subject = template.getSubject(request);

        assertThat(subject).contains("Test User");
        assertThat(subject).contains("job-789");
    }

    @Test
    void getRecipientEmailReturnsCandidateEmail() {
        ScreeningNotificationRequest request = createRequest("Email User");
        request.getCandidateDetails().setEmail("user@example.com");

        String email = template.getRecipientEmail(request);

        assertThat(email).isEqualTo("user@example.com");
    }

    @Test
    void getRecipientEmailReturnsNullWhenMissing() {
        ScreeningNotificationRequest request = createRequest("No Email");
        request.getCandidateDetails().setEmail(null);

        String email = template.getRecipientEmail(request);

        assertThat(email).isNull();
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