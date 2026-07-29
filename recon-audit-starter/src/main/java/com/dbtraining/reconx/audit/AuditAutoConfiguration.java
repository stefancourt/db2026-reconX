package com.dbtraining.reconx.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * TICKET-ADV095 — Spring Boot auto-configuration for audit event publishing.
 *
 * Automatically registers AuditEventPublisher as a Spring Bean when:
 * 1. ApplicationEventPublisher is on the classpath (always true in Spring Boot)
 * 2. reconx.audit.enabled is true (default) or not set
 * 3. No other AuditEventPublisher bean is already defined
 */
@AutoConfiguration
@ConditionalOnClass(ApplicationEventPublisher.class)
@ConditionalOnProperty(
    prefix = "reconx.audit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditEventPublisher auditEventPublisher(
        ApplicationEventPublisher publisher,
        AuditProperties props
    ) {
        return new AuditEventPublisher(publisher, props);
    }
}
