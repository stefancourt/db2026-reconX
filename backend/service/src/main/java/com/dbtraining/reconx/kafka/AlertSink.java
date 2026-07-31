package com.dbtraining.reconx.kafka;

/**
 * ============================================================================
 * TICKET-ADV133 — pluggable alert sink
 *
 * WHAT:    Where an alert goes after AlertConsumer has logged it.
 * HOW:     One method, one implementation in the training project
 *          ({@link NoopAlertSink}). Swap in a Slack / PagerDuty webhook
 *          implementation and nothing in the consumer changes.
 * WHY:     Keeps the Kafka concern (consume) separate from the notification
 *          concern (deliver), so the consumer stays unit-testable without a
 *          live webhook.
 * ============================================================================
 */
public interface AlertSink {

    void notify(String payload);
}
