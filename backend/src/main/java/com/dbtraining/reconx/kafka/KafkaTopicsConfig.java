package com.dbtraining.reconx.kafka;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * ============================================================================
 * TICKET-ADV128 — Declare Kafka topics on app start (3 main topics + DLQ)
 * TICKET-ADV134 — DLQ topic must be pre-declared for DeadLetterPublishingRecoverer
 *
 * WHAT:    Creates 4 topics if they don't already exist (trade-events,
 *          recon-results, system-alerts, trade-events-dlq).
 * HOW:     NewTopic @Bean instances are picked up by KafkaAdmin which
 *          creates them at application startup.
 * WHY:     Auto-create in code keeps `docker compose up` -> "ready" in one
 *          step. No manual `kafka-topics --create` ceremony.
 * OBSERVE: Kafdrop (http://localhost:9000) lists all 4 topics after startup.
 * ============================================================================
 *
 *  TODO(TICKET-ADV128 + ADV134):
 *    @Bean public NewTopic tradeEvents() {
 *        return TopicBuilder.name("trade-events").partitions(3).replicas(1).build();
 *    }
 *    @Bean public NewTopic reconResults() {
 *        return TopicBuilder.name("recon-results").partitions(2).replicas(1).build();
 *    }
 *    @Bean public NewTopic systemAlerts() {
 *        return TopicBuilder.name("system-alerts").partitions(1).replicas(1).build();
 *    }
 *    @Bean public NewTopic tradeEventsDlq() {
 *        return TopicBuilder.name("trade-events-dlq").partitions(3).replicas(1).build();
 *    }
 *
 *  HINT: Class is @Profile("!dev & !test") so it only runs in real
 *        environments — in dev / test the embedded Kafka or compose stack
 *        is expected to have the topics already.
 * ============================================================================
 */
@Configuration
@Profile("!dev & !test")
public class KafkaTopicsConfig {

    // TODO(TICKET-ADV128 + ADV134): declare the 4 NewTopic @Beans — see comments above.
}
