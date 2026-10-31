package com.notificationsystem.template;

import com.notificationsystem.model.ScreeningNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class SmsTemplate implements NotificationTemplate {
    private static final String SMS_TEMPLATE = """
            Hi {{candidate_name}}, {{message}} Book here: {{booking_url}} - Ref: {{job_id}}
            """;

    @Override
    public String render(ScreeningNotificationRequest request) {
        String candidateName = request.getCandidateDetails() != null ? request.getCandidateDetails().getName() : "Candidate";
        String message = request.getNotificationDetails() != null ? request.getNotificationDetails().getMessage() : "Please book your screening call.";
        String bookingUrl = request.getNotificationDetails() != null ? request.getNotificationDetails().getBookingUrl() : "";
        String jobId = request.getJobId();

        return SMS_TEMPLATE
                .replace("{{candidate_name}}", candidateName)
                .replace("{{message}}", message)
                .replace("{{booking_url}}", bookingUrl)
                .replace("{{job_id}}", jobId);
    }

    public String getRecipientPhone(ScreeningNotificationRequest request) {
        return request.getCandidateDetails() != null ? request.getCandidateDetails().getPhone() : null;
    }
}