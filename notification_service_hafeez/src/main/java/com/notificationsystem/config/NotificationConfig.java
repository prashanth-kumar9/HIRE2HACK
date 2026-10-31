package com.notificationsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {
    private String kafkaTopic;
    private String emailEndpoint;
    private String smsEndpoint;
    private int maxRetries;
    private long baseDelayMs;
    private Http http = new Http();

    // Getters and Setters
    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }

    public String getEmailEndpoint() {
        return emailEndpoint;
    }

    public void setEmailEndpoint(String emailEndpoint) {
        this.emailEndpoint = emailEndpoint;
    }

    public String getSmsEndpoint() {
        return smsEndpoint;
    }

    public void setSmsEndpoint(String smsEndpoint) {
        this.smsEndpoint = smsEndpoint;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public void setBaseDelayMs(long baseDelayMs) {
        this.baseDelayMs = baseDelayMs;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public int getConnectTimeout() {
        return http != null ? http.getConnectTimeout() : 0;
    }

    public int getReadTimeout() {
        return http != null ? http.getReadTimeout() : 0;
    }

    public static class Http {
        private int connectTimeout;
        private int readTimeout;

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}