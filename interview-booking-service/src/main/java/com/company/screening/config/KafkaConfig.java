package com.company.screening.config;

import com.company.screening.infrastructure.kafka.ScreeningNotificationProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic screeningNotificationTopic() {
        return TopicBuilder.name(ScreeningNotificationProducer.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
