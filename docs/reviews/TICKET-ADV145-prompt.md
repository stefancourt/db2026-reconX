# TICKET-ADV145 Kafka consumer config review prompt

Review the following Spring Kafka consumer configuration for production
readiness. Flag any missing or risky settings in these areas:

1. backpressure & poll tuning
2. error handling, retry & DLQ
3. idempotence and exactly-once semantics
4. observability - metrics, logging, traces
5. security - TLS, SASL, ACLs

For each finding, give the concrete config key, the recommended value, and a
one-line justification. Do NOT rewrite the whole file - just list findings.

Application context: trade reconciliation service, ~500 events/sec, strict
audit requirements.

=== application.yml (Kafka section) ===
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        enable.idempotence: true
        spring.json.add.type.headers: false
    consumer:
      group-id: reconx-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        max.poll.records: 100
        spring.json.trusted.packages: com.dbtraining.reconx.dto
        spring.json.use.type.headers: false
        spring.json.value.default.type: com.dbtraining.reconx.dto.TradeEvent

=== KafkaErrorHandlerConfig.java ===
package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (ConsumerRecord<?, ?> rec, Exception ex) ->
                        new TopicPartition(rec.topic() + "-dlq", rec.partition()));

        ExponentialBackOff backoff = new ExponentialBackOff(1000L, 2.0);
        backoff.setMaxAttempts(3);
        backoff.setMaxElapsedTime(8_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class);

        return handler;
    }
}