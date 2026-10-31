package com.notificationsystem.template;

import com.notificationsystem.model.ScreeningNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplate implements NotificationTemplate {
    private static final String EMAIL_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Screening Invitation</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background-color: #f8f9fa; border-radius: 8px; padding: 30px; border: 1px solid #e9ecef;">
                    <h2 style="color: #2c3e50; margin-top: 0;">Screening Call Invitation</h2>

                    <p>Hi <strong>{{candidate_name}}</strong>,</p>

                    <p>{{message}}</p>

                    <div style="text-align: center; margin: 30px 0;">
                        <a href="{{booking_url}}"
                           style="background-color: #3498db; color: white; padding: 14px 28px;
                                  text-decoration: none; border-radius: 5px; display: inline-block;
                                  font-weight: bold;">
                            Book Your Screening Call
                        </a>
                    </div>

                    <p style="color: #6c757d; font-size: 14px;">
                        If the button above doesn't work, you can copy and paste this link into your browser:<br>
                        <a href="{{booking_url}}">{{booking_url}}</a>
                    </p>

                    <hr style="border: none; border-top: 1px solid #e9ecef; margin: 30px 0;">

                    <p style="color: #6c757d; font-size: 12px; margin: 0;">
                        This is an automated message. Please do not reply to this email.<br>
                        Reference: {{job_id}} / {{candidate_id}} / {{application_id}}
                    </p>
                </div>
            </body>
            </html>
            """;

    @Override
    public String render(ScreeningNotificationRequest request) {
        String candidateName = request.getCandidateDetails() != null ? request.getCandidateDetails().getName() : "Candidate";
        String message = request.getNotificationDetails() != null ? request.getNotificationDetails().getMessage() : "Please book your screening call.";
        String bookingUrl = request.getNotificationDetails() != null ? request.getNotificationDetails().getBookingUrl() : "#";
        String jobId = request.getJobId();
        String candidateId = request.getCandidateId();
        String applicationId = request.getApplicationId();

        return EMAIL_TEMPLATE
                .replace("{{candidate_name}}", candidateName)
                .replace("{{message}}", message)
                .replace("{{booking_url}}", bookingUrl)
                .replace("{{job_id}}", jobId)
                .replace("{{candidate_id}}", candidateId)
                .replace("{{application_id}}", applicationId);
    }

    public String getSubject(ScreeningNotificationRequest request) {
        String candidateName = request.getCandidateDetails() != null ? request.getCandidateDetails().getName() : "Candidate";
        return "Screening Invitation for " + candidateName + " - " + request.getJobId();
    }

    public String getRecipientEmail(ScreeningNotificationRequest request) {
        return request.getCandidateDetails() != null ? request.getCandidateDetails().getEmail() : null;
    }
}