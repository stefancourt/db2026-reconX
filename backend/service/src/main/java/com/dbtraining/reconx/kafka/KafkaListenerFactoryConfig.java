package com.dbtraining.reconx.kafka;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

/**
 * ============================================================================
 * TICKET-ADV133 — listener container factory for `system-alerts`
 *
 * WHAT:    A String-in / String-out consumer factory used only by
 *          {@link AlertConsumer}.
 * HOW:     Copies the auto-configured consumer properties, then overrides
 *          both deserialisers with StringDeserializer.
 * WHY:     application.yml pins the global value deserialiser to
 *          JsonDeserializer with spring.json.value.default.type =
 *          com.dbtraining.reconx.dto.TradeEvent. Alerts are free-form text,
 *          not TradeEvents, so the default factory would throw on every
 *          record. Trade-event listeners keep using the auto-configured
 *          factory untouched.
 * OBSERVE: Kafdrop -> Consumers shows `alert-service` on the single
 *          `system-alerts` partition with lag 0.
 * ============================================================================
 */
@Configuration
public class KafkaListenerFactoryConfig {

    @Bean
    public ConsumerFactory<String, String> systemAlertConsumerFactory(KafkaProperties properties) {
        // buildConsumerProperties() gives bootstrap servers, group defaults and
        // auto-offset-reset from application.yml; only the deserialisers differ.
        Map<String, Object> props = new HashMap<>(properties.buildConsumerProperties(null));
        props.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> systemAlertListenerContainerFactory(
            ConsumerFactory<String, String> systemAlertConsumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(systemAlertConsumerFactory);
        // TICKET-ADV134/ADV135 — same retry + DLQ policy as the trade-events listeners.
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
