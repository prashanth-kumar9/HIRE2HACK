package com.notificationsystem.client;

import com.notificationsystem.config.NotificationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationHttpClientTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private NotificationHttpClient client;
    private NotificationConfig config;

    @BeforeEach
    void setUp() {
        config = new NotificationConfig();
        config.setEmailEndpoint("http://localhost:8080/api/email/send");
        config.setSmsEndpoint("http://localhost:8080/api/sms/send");
        config.setMaxRetries(3);
        config.setBaseDelayMs(1000);
        NotificationConfig.Http http = new NotificationConfig.Http();
        http.setConnectTimeout(10000);
        http.setReadTimeout(30000);
        config.setHttp(http);

        client = new NotificationHttpClient(objectMapper, config);
    }

    @Test
    void isProcessedReturnsFalseForNewKey() {
        assertThat(client.isProcessed("new-key")).isFalse();
    }

    @Test
    void markProcessedAndIsProcessed() {
        client.markProcessed("test-key");
        assertThat(client.isProcessed("test-key")).isTrue();
    }

    @Test
    void sendEmailReturnsAlreadySentForProcessedKey() throws IOException, InterruptedException {
        client.markProcessed("key123:email");

        NotificationHttpClient.EmailResponse response = client.sendEmail("key123", "test@example.com", "Subject", "Body");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Already sent");
    }

    @Test
    void sendSmsReturnsAlreadySentForProcessedKey() throws IOException, InterruptedException {
        client.markProcessed("key123:sms");

        NotificationHttpClient.SmsResponse response = client.sendSms("key123", "+1234567890", "Message");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Already sent");
    }
}