package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV131 — ReconciliationConsumer
 *
 * WHAT:    Listens for `trade-events` and schedules a reconciliation job.
 * HOW:     @KafkaListener on `trade-events`, groupId `recon-service`. In the
 *          full implementation this would insert a row into recon_jobs and
 *          trigger the engine; the trainer reference logs the trigger so
 *          students can trace the message flow end-to-end.
 * WHY:     Decouples "trade saved" from "trade reconciled" so a slow recon
 *          run never blocks the trade-write path.
 * OBSERVE: A POST /api/v1/trades shows up here as a log line referencing the
 *          same eventId emitted by TradeEventProducer.
 * ============================================================================
 *
 *  TODO(TICKET-ADV131):
 *    @KafkaListener(topics = "trade-events", groupId = "recon-service")
 *    public void onTradeEvent(TradeEvent event) {
 *        log.info("Recon-trigger received eventId={} ref={} type={}",
 *                 event.eventId(), event.tradeRef(), event.eventType());
 *        // enqueue a recon job (do NOT reconcile inline — that would block
 *        // the consumer thread and back up the partition).
 *    }
 * ============================================================================
 */
@Component
public class ReconciliationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationConsumer.class);

    public void onTradeEvent(TradeEvent event) {
        throw new UnsupportedOperationException("TICKET-ADV131");
    }
}
