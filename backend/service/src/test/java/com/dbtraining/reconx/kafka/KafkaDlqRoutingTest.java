package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV134 / TICKET-ADV135 — retry + DLQ routing, against a real broker.
 *
 * A listener that always throws is the whole point: it proves the record is
 * retried with backoff and then lands on `trade-events-dlq` on the SAME
 * partition number, instead of stalling the partition forever.
 *
 * Embedded broker, no Docker — so this runs under surefire (`mvn test`), not
 * failsafe.
 * ============================================================================
 */
@SpringBootTest(
        classes = KafkaDlqRoutingTest.TestApp.class,
        // earliest: the failing listener must see the record even if it produced
        // before the consumer finished joining the group.
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@EmbeddedKafka(
        topics = {KafkaDlqRoutingTest.TOPIC, KafkaDlqRoutingTest.DLQ_TOPIC},
        partitions = 3,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaDlqRoutingTest {

    static final String TOPIC = "trade-events";
    static final String DLQ_TOPIC = "trade-events-dlq";

    /** The partition the record is produced to — and the one the DLQ must reuse. */
    private static final int SOURCE_PARTITION = 1;

    @SpringBootConfiguration
    @ImportAutoConfiguration({KafkaAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import(KafkaErrorHandlerConfig.class)
    static class TestApp {

        @Bean
        AlwaysFailingListener alwaysFailingListener() {
            return new AlwaysFailingListener();
        }
    }

    static class AlwaysFailingListener {

        final AtomicInteger attempts = new AtomicInteger();

        @KafkaListener(topics = TOPIC, groupId = "dlq-routing-test")
        public void onTradeEvent(String payload) {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        }
    }

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private AlwaysFailingListener listener;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, String> dlqConsumer;

    @AfterEach
    void closeConsumer() {
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
    }

    @Test
    void failingListener_retriesWithBackoff_thenPublishesToDlqOnTheSamePartition() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                broker.getBrokersAsString(), "dlq-assert", "true");
        // The recoverer forwards the original value bytes; read them back as a
        // plain String rather than letting JsonDeserializer guess a type.
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        dlqConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
                consumerProps,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer());
        broker.consumeFromAnEmbeddedTopic(dlqConsumer, DLQ_TOPIC);

        long start = System.currentTimeMillis();
        template.send(new ProducerRecord<>(TOPIC, SOURCE_PARTITION, "TRD-20260315-0001", "{\"tradeRef\":\"TRD-20260315-0001\"}"));

        ConsumerRecord<String, String> dlqRecord =
                KafkaTestUtils.getSingleRecord(dlqConsumer, DLQ_TOPIC, Duration.ofSeconds(30));
        long elapsedMs = System.currentTimeMillis() - start;

        // TICKET-ADV134 — same partition number on the DLQ as on the source topic.
        assertThat(dlqRecord.partition()).isEqualTo(SOURCE_PARTITION);
        assertThat(dlqRecord.key()).isEqualTo("TRD-20260315-0001");
        // ...and the kafka_dlt-* headers carry the original coordinates.
        assertThat(dlqRecord.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
        assertThat(new String(dlqRecord.headers().lastHeader("kafka_dlt-original-topic").value()))
                .isEqualTo(TOPIC);
        assertThat(new String(dlqRecord.headers().lastHeader("kafka_dlt-exception-message").value()))
                .contains("boom");

        // TICKET-ADV135 — the record was retried, not dropped on the first failure,
        // and the retries were spaced out (1s + 2s before the third attempt).
        assertThat(listener.attempts.get()).isGreaterThanOrEqualTo(3);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(3_000L);
    }
}
