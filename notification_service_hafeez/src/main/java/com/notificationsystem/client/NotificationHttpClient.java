package com.notificationsystem.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationHttpClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationHttpClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String emailEndpoint;
    private final String smsEndpoint;
    private final int maxRetries;
    private final long baseDelayMs;
    private final int connectTimeout;
    private final int readTimeout;

    // Idempotency store - in production, use Redis or database
    private final Map<String, Boolean> processedRequests = new ConcurrentHashMap<>();

    @Autowired
    public NotificationHttpClient(
            ObjectMapper objectMapper,
            com.notificationsystem.config.NotificationConfig config) {
        this.objectMapper = objectMapper;
        this.emailEndpoint = config.getEmailEndpoint();
        this.smsEndpoint = config.getSmsEndpoint();
        this.maxRetries = config.getMaxRetries();
        this.baseDelayMs = config.getBaseDelayMs();
        this.connectTimeout = config.getConnectTimeout();
        this.readTimeout = config.getReadTimeout();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
    }

    public boolean isProcessed(String idempotencyKey) {
        return processedRequests.containsKey(idempotencyKey);
    }

    public void markProcessed(String idempotencyKey) {
        processedRequests.put(idempotencyKey, true);
    }

    public EmailResponse sendEmail(String idempotencyKey, String to, String subject, String htmlBody)
            throws IOException, InterruptedException {
        String key = idempotencyKey + ":email";
        if (isProcessed(key)) {
            log.info("Email already sent for idempotency key: {}", idempotencyKey);
            return new EmailResponse(true, "Already sent", null);
        }

        EmailRequest request = new EmailRequest(to, subject, htmlBody);
        String jsonBody = toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(emailEndpoint))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMillis(readTimeout))
                .build();

        HttpResponse<String> response = executeWithRetry(httpRequest);
        EmailResponse emailResponse = fromJson(response.body(), EmailResponse.class);

        if (emailResponse.isSuccess()) {
            markProcessed(key);
        }

        return emailResponse;
    }

    public SmsResponse sendSms(String idempotencyKey, String to, String message)
            throws IOException, InterruptedException {
        String key = idempotencyKey + ":sms";
        if (isProcessed(key)) {
            log.info("SMS already sent for idempotency key: {}", idempotencyKey);
            return new SmsResponse(true, "Already sent", null);
        }

        SmsRequest request = new SmsRequest(to, message);
        String jsonBody = toJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(smsEndpoint))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMillis(readTimeout))
                .build();

        HttpResponse<String> response = executeWithRetry(httpRequest);
        SmsResponse smsResponse = fromJson(response.body(), SmsResponse.class);

        if (smsResponse.isSuccess()) {
            markProcessed(key);
        }

        return smsResponse;
    }

    private HttpResponse<String> executeWithRetry(HttpRequest request) throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response;
                }

                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    // Retryable status codes
                    lastException = new IOException("HTTP " + response.statusCode() + ": " + response.body());
                } else {
                    // Non-retryable error
                    return response;
                }
            } catch (IOException | InterruptedException e) {
                lastException = e instanceof IOException ? (IOException) e : new IOException(e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw (InterruptedException) e;
                }
            }

            if (attempt < maxRetries) {
                long delay = baseDelayMs * (1L << attempt); // Exponential backoff
                log.warn("Attempt {} failed, retrying in {}ms...", attempt + 1, delay);
                Thread.sleep(delay);
            }
        }

        throw new IOException("Max retries exceeded", lastException);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize response", e);
        }
    }

    public static class EmailRequest {
        private String to;
        private String subject;
        private String htmlBody;

        public EmailRequest() {}

        public EmailRequest(String to, String subject, String htmlBody) {
            this.to = to;
            this.subject = subject;
            this.htmlBody = htmlBody;
        }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getHtmlBody() { return htmlBody; }
        public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
    }

    public static class EmailResponse {
        private boolean success;
        private String message;
        private String messageId;

        public EmailResponse() {}
        public EmailResponse(boolean success, String message, String messageId) {
            this.success = success;
            this.message = message;
            this.messageId = messageId;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
    }

    public static class SmsRequest {
        private String to;
        private String message;

        public SmsRequest() {}

        public SmsRequest(String to, String message) {
            this.to = to;
            this.message = message;
        }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class SmsResponse {
        private boolean success;
        private String message;
        private String messageId;

        public SmsResponse() {}
        public SmsResponse(boolean success, String message, String messageId) {
            this.success = success;
            this.message = message;
            this.messageId = messageId;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
    }
}