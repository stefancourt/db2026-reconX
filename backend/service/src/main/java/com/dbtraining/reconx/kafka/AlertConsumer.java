package com.dbtraining.reconx.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV133 — AlertConsumer
 *
 * WHAT:    Subscribes to `system-alerts` and (for the training project) logs
 *          the payload. In a real environment this is where Slack / PagerDuty
 *          / e-mail fan-out would happen — see {@link AlertSink}.
 * HOW:     @KafkaListener on the `system-alerts` topic, groupId
 *          `alert-service`.
 * WHY:     Decouples alert producers (any service) from alert sinks
 *          (notification channels).
 * OBSERVE: Publish a string to `system-alerts` via Kafdrop -> a WARN line
 *          appears in the app log.
 *
 * GOTCHA:  containerFactory is NOT optional here. The auto-configured factory
 *          uses JsonDeserializer with spring.json.value.default.type =
 *          dto.TradeEvent (application.yml), so a plain alert string would
 *          fail to deserialise on every message. `system-alerts` is also a
 *          single-partition topic — strict global ordering of alerts beats
 *          consumer parallelism.
 * ============================================================================
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AlertConsumer {

    private final AlertSink sink;

    @KafkaListener(
            topics = "system-alerts",
            groupId = "alert-service",
            containerFactory = "systemAlertListenerContainerFactory"
    )
    public void onAlert(String payload) {
        log.warn("ALERT: {}", payload);
        sink.notify(payload);
    }
}
