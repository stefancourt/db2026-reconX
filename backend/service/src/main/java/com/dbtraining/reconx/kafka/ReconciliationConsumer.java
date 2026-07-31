package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.service.ReconciliationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for trade-events and schedules reconciliation.
 *
 * In a full implementation this would push a row into recon_jobs and
 * trigger the engine. The trainer copy logs the trigger so students can
 * trace the message flow end-to-end without a full job runner.
 */
@Component
public class ReconciliationConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(ReconciliationConsumer.class);

    private final ReconciliationEngine reconEngine;

    public ReconciliationConsumer(ReconciliationEngine reconEngine) {
        this.reconEngine = reconEngine;
    }

    @KafkaListener(
            topics = "trade-events",
            groupId = "recon-service",
            containerFactory = "tradeEventListenerContainerFactory"
    )
    public void onTradeEvent(TradeEvent event) {
        log.debug("Recon-trigger received eventId={} ref={} type={}",
                event.eventId(),
                event.tradeRef(),
                event.eventType());

        switch (event.eventType()) {
            case TRADE_CREATED, TRADE_UPDATED ->
                    reconEngine.scheduleRecon(event.tradeRef());

            case TRADE_CANCELLED ->
                    reconEngine.cancelPendingRecon(event.tradeRef());
        }
    }
}