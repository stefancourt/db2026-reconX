package com.dbtraining.reconx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the ReconX trade reconciliation service.
 *
 * <p>Activated capabilities:
 * <ul>
 *   <li>{@link EnableCaching}    — ADV081 @Cacheable on InstrumentService.</li>
 *   <li>{@link EnableKafka}      — ADV128–ADV133 Kafka producers and @KafkaListener consumers.</li>
 *   <li>{@link EnableAsync}      — ADV037 CompletableFuture-based parallel reconciliation.</li>
 * </ul>
 */
/* JPA auditing is enabled by {@code config.JpaConfig} (ADV050), not here — declaring
 * @EnableJpaAuditing in both places registers the jpaAuditingHandler bean twice and
 * the context fails with BeanDefinitionOverrideException. */
@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableAsync
public class ReconxApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconxApplication.class, args);
    }
}
