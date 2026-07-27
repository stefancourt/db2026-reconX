package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * ============================================================================
 * TICKET-ADV128 — Declare Kafka topics on app start (3 main topics + DLQ)
 * TICKET-ADV134 — DLQ topic must be pre-declared for DeadLetterPublishingRecoverer
 * ============================================================================
 */
@Configuration
@Profile("!dev & !test")
public class KafkaTopicsConfig {

    @Bean
    public NewTopic tradeEvents() {
        return TopicBuilder.name("trade-events").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reconResults() {
        return TopicBuilder.name("recon-results").partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic systemAlerts() {
        return TopicBuilder.name("system-alerts").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic tradeEventsDlq() {
        return TopicBuilder.name("trade-events-dlq").partitions(3).replicas(1).build();
    }
}
