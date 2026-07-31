package com.dbtraining.reconx.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV133 — default AlertSink
 *
 * WHAT:    Does nothing beyond a debug line. The default wiring so the app
 *          boots without a Slack / PagerDuty webhook configured.
 * WHY:     A missing sink bean would fail AlertConsumer's constructor
 *          injection at startup; a no-op keeps dev and test green.
 * ============================================================================
 */
@Component
@Slf4j
public class NoopAlertSink implements AlertSink {

    @Override
    public void notify(String payload) {
        log.debug("NoopAlertSink swallowed alert: {}", payload);
    }
}
