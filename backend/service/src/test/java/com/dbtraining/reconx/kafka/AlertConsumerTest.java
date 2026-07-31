package com.dbtraining.reconx.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * TICKET-ADV133 — the consumer's own contract: log the alert, then hand it to
 * the pluggable sink. The @KafkaListener wiring (topic / groupId / factory) is
 * asserted from the annotation because it is the part a refactor silently
 * breaks — a wrong groupId still compiles and still starts.
 */
@ExtendWith(MockitoExtension.class)
class AlertConsumerTest {

    @Mock
    private AlertSink sink;

    @InjectMocks
    private AlertConsumer consumer;

    @Test
    void onAlert_forwardsPayloadToSink() {
        consumer.onAlert("OPS-NEW severity=HIGH recon lag over threshold");

        verify(sink).notify("OPS-NEW severity=HIGH recon lag over threshold");
        verifyNoMoreInteractions(sink);
    }

    @Test
    void listener_isBoundToSystemAlertsWithItsOwnGroupAndFactory() throws Exception {
        Method onAlert = AlertConsumer.class.getMethod("onAlert", String.class);
        KafkaListener listener = onAlert.getAnnotation(KafkaListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.topics()).containsExactly("system-alerts");
        // Distinct groupId is mandatory for fan-out: sharing a group with the
        // recon or audit consumers would split the partitions instead.
        assertThat(listener.groupId()).isEqualTo("alert-service");
        // String payloads need the dedicated factory — the auto-configured one
        // deserialises every value as a TradeEvent.
        assertThat(listener.containerFactory()).isEqualTo("systemAlertListenerContainerFactory");
    }
}
