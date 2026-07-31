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

/**
 * ============================================================================
 * TICKET-ADV134 — DLQ via DeadLetterPublishingRecoverer (failed messages
 *                routed to {topic}-dlq with the same partition number)
 * TICKET-ADV135 — Retry strategy: 3 attempts with exponential backoff
 *                (1s, 2s, 4s) before giving up to DLQ
 *
 * WHAT:    Spring Kafka error handler that retries with backoff and on
 *          final failure publishes the poison record to the corresponding
 *          DLQ topic.
 * HOW:     One @Bean DefaultErrorHandler combining a
 *          DeadLetterPublishingRecoverer + ExponentialBackOff. Boot applies
 *          the single CommonErrorHandler bean to its auto-configured listener
 *          container factories, so every @KafkaListener inherits it.
 * WHY:     Without this, an exception in a listener kills the consumer
 *          thread and the whole partition stalls. With it, retries happen,
 *          and a final failure is observable (DLQ topic) rather than lost.
 * OBSERVE: Force an exception in a consumer — Kafdrop should show the
 *          record on `trade-events-dlq` with the same partition as the
 *          original.
 *
 * GOTCHA:  trade-events-dlq must already exist (TICKET-ADV128). The
 *          recoverer does NOT auto-create the topic.
 * ============================================================================
 */
@Configuration
public class KafkaErrorHandlerConfig {

    /**
     * Injected as KafkaOperations, not KafkaTemplate: Boot's auto-configured bean
     * is a {@code KafkaTemplate<?, ?>}, so asking for a concrete
     * {@code KafkaTemplate<Object, Object>} would not resolve.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> template) {

        // TICKET-ADV134 — same partition number on the DLQ as on the source topic,
        // so ordering per key survives the round trip and ops can correlate offsets.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (ConsumerRecord<?, ?> rec, Exception ex) ->
                        new TopicPartition(rec.topic() + "-dlq", rec.partition()));

        // TICKET-ADV135 — 1s, 2s, 4s. Fixed backoff would hammer a downstream that
        // is already struggling; exponential gives it room to recover.
        ExponentialBackOff backoff = new ExponentialBackOff(1000L, 2.0);
        backoff.setMaxAttempts(3);
        backoff.setMaxElapsedTime(8_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);

        // A poison pill will never deserialise, no matter how often it is retried —
        // send it straight to the DLQ instead of burning the retry budget.
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class);

        return handler;
    }
}
