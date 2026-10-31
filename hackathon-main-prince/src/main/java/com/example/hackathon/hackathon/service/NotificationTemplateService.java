package com.example.hackathon.hackathon.service;

import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {

    public String getSmsTemplate() {
        return "Congratulations! You have been selected for an interview. Please check your registered email for further details.";
    }

    public String getEmail24HrsSubject() {
        return "Interview Selection - Follow-up";
    }

    public String getEmail24HrsBody() {
        return "Congratulations on being selected for the interview. This is a follow-up notification regarding your interview selection. Please review the interview details and next steps.";
    }

    public String getEmail48HrsSubject() {
        return "Interview Selection - Reminder";
    }

    public String getEmail48HrsBody() {
        return "This is a reminder regarding your interview selection. Please review the interview details and complete any required next steps.";
    }
}
