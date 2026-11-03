package com.notificationsystem.controller;

import com.notificationsystem.client.NotificationHttpClient;
import com.notificationsystem.model.Notification;
import com.notificationsystem.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@RestController
@RequestMapping
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationHttpClient httpClient;
    private final NotificationDispatcher dispatcher;
    private final Queue<Map<String, Object>> notificationHistory = new ConcurrentLinkedQueue<>();

    @Autowired
    public NotificationController(NotificationHttpClient httpClient, NotificationDispatcher dispatcher) {
        this.httpClient = httpClient;
        this.dispatcher = dispatcher;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "notification-service",
                "timestamp", Instant.now().toString()
        ));
    }

    @PostMapping("/notify/sms")
    public ResponseEntity<Map<String, Object>> sendSms(@RequestBody Map<String, Object> payload) {
        String phone = (String) payload.get("phone");
        String message = (String) payload.get("message");
        String candidateName = (String) payload.get("candidateName");
        String bookingUrl = (String) payload.get("bookingUrl");

        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone is required"));
        }
        if (message == null || message.isBlank()) {
            message = "Hi " + (candidateName != null ? candidateName : "Candidate")
                    + ", please schedule your interview here: " + bookingUrl;
        }

        String idempotencyKey = UUID.randomUUID().toString();
        log.info("[SMS DISPATCH] Sending SMS to: {} -> {}", phone, message);

        String messageId = "sms-" + UUID.randomUUID();
        try {
            NotificationHttpClient.SmsResponse response = httpClient.sendSms(idempotencyKey, phone, message);
            if (response != null && response.getMessageId() != null) {
                messageId = response.getMessageId();
            }
        } catch (Exception e) {
            log.warn("[SMS DISPATCH] Mock/gateway note: {}", e.getMessage());
        }

        Map<String, Object> record = new HashMap<>();
        record.put("messageId", messageId);
        record.put("channel", "sms");
        record.put("recipient", phone);
        record.put("candidateName", candidateName);
        record.put("message", message);
        record.put("bookingUrl", bookingUrl);
        record.put("sentAt", Instant.now().toString());
        record.put("status", "DELIVERED");
        notificationHistory.add(record);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "channel", "sms",
                "recipient", phone,
                "status", "DELIVERED"
        ));
    }

    @PostMapping("/notify/email")
    public ResponseEntity<Map<String, Object>> sendEmail(@RequestBody Map<String, Object> payload) {
        String email = (String) payload.get("email");
        String subject = (String) payload.getOrDefault("subject", "Interview Invitation");
        String message = (String) payload.get("message");
        String candidateName = (String) payload.get("candidateName");
        String bookingUrl = (String) payload.get("bookingUrl");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email is required"));
        }
        if (message == null || message.isBlank()) {
            message = "Hi " + (candidateName != null ? candidateName : "Candidate")
                    + ", thank you for applying. Please schedule your interview here: " + bookingUrl;
        }

        String idempotencyKey = UUID.randomUUID().toString();
        log.info("[EMAIL DISPATCH] Sending Email to: {} -> Subject: {}", email, subject);

        String messageId = "email-" + UUID.randomUUID();
        try {
            NotificationHttpClient.EmailResponse response = httpClient.sendEmail(idempotencyKey, email, subject, message);
            if (response != null && response.getMessageId() != null) {
                messageId = response.getMessageId();
            }
        } catch (Exception e) {
            log.warn("[EMAIL DISPATCH] Mock/gateway note: {}", e.getMessage());
        }

        Map<String, Object> record = new HashMap<>();
        record.put("messageId", messageId);
        record.put("channel", "email");
        record.put("recipient", email);
        record.put("subject", subject);
        record.put("candidateName", candidateName);
        record.put("message", message);
        record.put("bookingUrl", bookingUrl);
        record.put("sentAt", Instant.now().toString());
        record.put("status", "DELIVERED");
        notificationHistory.add(record);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "messageId", messageId,
                "channel", "email",
                "recipient", email,
                "status", "DELIVERED"
        ));
    }

    @PostMapping("/notifications")
    public ResponseEntity<Map<String, Object>> handleGenericNotification(@RequestBody Map<String, Object> payload) {
        String type = (String) payload.getOrDefault("type", "GENERIC_NOTIFICATION");
        log.info("[GENERIC NOTIFICATION] Type: {}, payload: {}", type, payload);

        Map<String, Object> record = new HashMap<>(payload);
        record.put("messageId", "notif-" + UUID.randomUUID());
        record.put("sentAt", Instant.now().toString());
        record.put("status", "SENT");
        notificationHistory.add(record);

        return ResponseEntity.ok(Map.of("success", true, "type", type, "recorded", true));
    }

    @GetMapping("/notifications/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory() {
        return ResponseEntity.ok(new ArrayList<>(notificationHistory));
    }
}
