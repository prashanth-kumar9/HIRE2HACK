package com.notificationsystem.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class ScreeningNotificationRequest {
    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("candidate_id")
    private String candidateId;

    @JsonProperty("application_id")
    private String applicationId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("candidate_details")
    private CandidateDetails candidateDetails;

    @JsonProperty("notification_preference")
    private NotificationPreference notificationPreference;

    @JsonProperty("notification_details")
    private NotificationDetails notificationDetails;

    @JsonProperty("metadata")
    private Metadata metadata;

    // Getters and Setters
    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public CandidateDetails getCandidateDetails() {
        return candidateDetails;
    }

    public void setCandidateDetails(CandidateDetails candidateDetails) {
        this.candidateDetails = candidateDetails;
    }

    public NotificationPreference getNotificationPreference() {
        return notificationPreference;
    }

    public void setNotificationPreference(NotificationPreference notificationPreference) {
        this.notificationPreference = notificationPreference;
    }

    public NotificationDetails getNotificationDetails() {
        return notificationDetails;
    }

    public void setNotificationDetails(NotificationDetails notificationDetails) {
        this.notificationDetails = notificationDetails;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public static class CandidateDetails {
        private String name;
        private String email;
        private String phone;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    public static class NotificationPreference {
        private boolean sms;
        private boolean email;
        private boolean whatsapp;
        private boolean voice;

        public boolean isSms() {
            return sms;
        }

        public void setSms(boolean sms) {
            this.sms = sms;
        }

        public boolean isEmail() {
            return email;
        }

        public void setEmail(boolean email) {
            this.email = email;
        }

        public boolean isWhatsapp() {
            return whatsapp;
        }

        public void setWhatsapp(boolean whatsapp) {
            this.whatsapp = whatsapp;
        }

        public boolean isVoice() {
            return voice;
        }

        public void setVoice(boolean voice) {
            this.voice = voice;
        }
    }

    public static class NotificationDetails {
        private String channel;

        @JsonProperty("template_id")
        private String templateId;

        private String message;

        @JsonProperty("booking_url")
        private String bookingUrl;

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getBookingUrl() {
            return bookingUrl;
        }

        public void setBookingUrl(String bookingUrl) {
            this.bookingUrl = bookingUrl;
        }
    }

    public static class Metadata {
        @JsonProperty("correlation_id")
        private String correlationId;

        @JsonProperty("event_timestamp")
        private String eventTimestamp;

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        public String getEventTimestamp() {
            return eventTimestamp;
        }

        public void setEventTimestamp(String eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
        }
    }

    public String getIdempotencyKey() {
        String correlationId = metadata != null ? metadata.getCorrelationId() : null;
        return correlationId != null ? correlationId : jobId + "-" + candidateId + "-" + eventType;
    }
}