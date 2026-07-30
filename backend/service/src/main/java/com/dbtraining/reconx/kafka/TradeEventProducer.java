package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV129 — TradeEventProducer
 *
 * WHAT:    Publishes TradeEvent messages to the `trade-events` Kafka topic.
 * HOW:     KafkaTemplate<String, TradeEvent>. Key = tradeRef so that all
 *          events for the same trade hash to the same partition and
 *          preserve ordering.
 * WHY:     Out-of-order events for the same trade would make event sourcing
 *          impossible (you'd "apply" CREATE after UPDATE).
 * OBSERVE: Kafdrop -> `trade-events` shows one message per published event,
 *          partitioned by tradeRef.
 * ============================================================================
 *
 *  TODO(TICKET-ADV129):
 *    public void publish(TradeEvent event) {
 *        log.debug("Publishing TradeEvent eventId={} ref={} type={}",
 *                  event.eventId(), event.tradeRef(), event.eventType());
 *        template.send(TOPIC, event.tradeRef(), event);
 *    }
 *
 *  GOTCHA: NEVER let a Kafka publish failure roll back the DB transaction.
 *          Publish AFTER commit (use TransactionSynchronizationManager or
 *          @TransactionalEventListener), or accept eventual consistency.
 * ============================================================================
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TradeEventProducer {

    // static final — initialised inline, so @RequiredArgsConstructor skips it.
    private static final String TOPIC = "trade-events";

    private final KafkaTemplate<String, TradeEvent> template;

    public void publish(TradeEvent event) {
        log.debug("Publishing TradeEvent eventId={} ref={} type={}",
                  event.eventId(), event.tradeRef(), event.eventType());
        template.send(TOPIC, event.tradeRef(), event);
    }
}
