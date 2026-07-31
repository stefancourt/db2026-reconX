package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.domain.DlqMessage;
import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * TICKET-ADV136 — the DLQ row must carry enough context for an operator to act
 * without opening Kafdrop: original topic (not the -dlq alias), partition,
 * offset, failure reason, and a replayable payload.
 */
@ExtendWith(MockitoExtension.class)
class DlqConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private DlqMessageRepository repo;

    @Captor
    private ArgumentCaptor<DlqMessage> saved;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private DlqConsumer consumer() {
        return new DlqConsumer(repo, objectMapper);
    }

    private static TradeEvent event() {
        return new TradeEvent(
                EVENT_ID,
                "TRD-20260315-0001",
                TradeEvent.EventType.TRADE_CREATED,
                Instant.parse("2026-03-15T10:15:30Z"),
                "trader@reconx.local",
                null,
                "{\"status\":\"NEW\"}");
    }

    private static ConsumerRecord<String, TradeEvent> record(TradeEvent value) {
        return new ConsumerRecord<>("trade-events-dlq", 2, 17L, "TRD-20260315-0001", value);
    }

    @Test
    void onDlqMessage_persistsFailureContext() {
        consumer().onDlqMessage(record(event()), "Listener failed; nested exception is java.lang.RuntimeException: boom");

        verify(repo).save(saved.capture());
        DlqMessage row = saved.getValue();

        assertThat(row.getEventId()).isEqualTo(EVENT_ID.toString());
        assertThat(row.getTradeRef()).isEqualTo("TRD-20260315-0001");
        // The row points at where the message came FROM, not at the DLQ itself —
        // that is the topic the replay endpoint has to publish back to.
        assertThat(row.getOriginalTopic()).isEqualTo("trade-events");
        assertThat(row.getPartition()).isEqualTo(2);
        assertThat(row.getOffset()).isEqualTo(17L);
        assertThat(row.getReason()).contains("boom");
        assertThat(row.getPayload()).contains("TRD-20260315-0001").contains("TRADE_CREATED");
        assertThat(row.getFirstSeen()).isNotNull();
    }

    @Test
    void onDlqMessage_withoutExceptionHeader_stillRecordsTheRow() {
        consumer().onDlqMessage(record(event()), null);

        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getReason()).contains("unknown");
    }

    @Test
    void onDlqMessage_withNullValue_fallsBackToTheRecordKey() {
        // A poison pill never deserialises, so the record value is null. The row
        // must still be written — losing it would hide the failure entirely.
        consumer().onDlqMessage(record(null), "DeserializationException");

        verify(repo).save(saved.capture());
        DlqMessage row = saved.getValue();

        assertThat(row.getTradeRef()).isEqualTo("TRD-20260315-0001");
        assertThat(row.getEventId()).isNotBlank();
        assertThat(row.getPayload()).isNull();
    }
}
