package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.domain.DlqMessage;
import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * TICKET-ADV136 — DlqConsumer
 *
 * WHAT:    Consumes `trade-events-dlq` and records every failure as a
 *          dlq_messages row: what failed, on which partition / offset, and
 *          why.
 * HOW:     @KafkaListener on `trade-events-dlq`, groupId `dlq-monitor`. The
 *          failure reason arrives in the kafka_dlt-exception-message header
 *          written by DeadLetterPublishingRecoverer (TICKET-ADV134).
 * WHY:     Kafka retention eventually drops the DLQ record. The operator
 *          still needs the failure inventory afterwards — and the replay
 *          endpoint needs the original payload to re-publish.
 * OBSERVE: Force a listener failure, then SELECT * FROM dlq_messages.
 *
 * GOTCHA:  The exception header is `required = false` on purpose. A record
 *          hand-produced onto the DLQ (Kafdrop, a replay test) carries no
 *          such header, and a required header would fail the listener —
 *          which would then DLQ the DLQ.
 * ============================================================================
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DlqConsumer {

    private final DlqMessageRepository repo;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "trade-events-dlq", groupId = "dlq-monitor")
    public void onDlqMessage(ConsumerRecord<String, TradeEvent> record,
                             @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exMsg) {

        TradeEvent event = record.value();
        String reason = exMsg != null ? exMsg : "unknown — no exception header on the DLQ record";

        // A deserialisation failure lands here with a null value: the bytes never
        // became a TradeEvent. Fall back to the record key (the tradeRef) and a
        // synthetic eventId so the row still gets written instead of NPE-ing.
        String eventId = event != null && event.eventId() != null
                ? event.eventId().toString()
                : UUID.randomUUID().toString();
        String tradeRef = event != null ? event.tradeRef() : record.key();

        log.error("DLQ: topic={} partition={} offset={} tradeRef={} eventId={} reason={}",
                record.topic(), record.partition(), record.offset(), tradeRef, eventId, reason);

        repo.save(DlqMessage.builder()
                .eventId(eventId)
                .tradeRef(tradeRef)
                .originalTopic(record.topic().replace("-dlq", ""))
                .partition(record.partition())
                .offset(record.offset())
                .payload(serialise(event))
                .reason(reason)
                .firstSeen(Instant.now())
                .build());
    }

    private String serialise(TradeEvent event) {
        if (event == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Never let a serialisation problem lose the DLQ row itself.
            log.warn("Could not serialise DLQ payload for eventId={}", event.eventId(), e);
            return null;
        }
    }
}
