package com.dbtraining.reconx.audit;

import org.springframework.context.ApplicationEventPublisher;

/**
 * TICKET-ADV095 — Audit event publisher auto-configured by the starter.
 *
 * Publishes audit events through Spring's event system; consumers can
 * listen via @EventListener on the domain events emitted here.
 */
public class AuditEventPublisher {
    private final ApplicationEventPublisher publisher;
    private final AuditProperties props;

    public AuditEventPublisher(ApplicationEventPublisher publisher, AuditProperties props) {
        this.publisher = publisher;
        this.props = props;
    }

    /**
     * Publish an audit event to the application event system.
     */
    public void publish(AuditEvent event) {
        if (props.isEnabled()) {
            publisher.publishEvent(event);
        }
    }

    /**
     * Simple audit event model.
     */
    public record AuditEvent(String action, String actor, String resource, String details) {
    }
}
