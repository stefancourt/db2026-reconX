package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * TICKET-ADV132 / ADV137 — Append-only audit row written by AuditEventConsumer.
 * Used for the event-sourcing rebuild of trade state (TICKET-ADV137).
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "trade_ref", nullable = false, length = 30)
    private String tradeRef;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(length = 100)
    private String actor;

    // No @Lob — Hibernate 6 + Postgres treats @Lob String as OID column,
    // but Liquibase translates CLOB to TEXT. columnDefinition keeps both DBs
    // happy (H2 accepts TEXT in Postgres mode, Postgres uses it natively).
    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    public AuditLogEntry() {}

    public AuditLogEntry(String eventId, String tradeRef, String eventType,
                         Instant ts, String actor, String before, String after) {
        this.eventId = eventId;
        this.tradeRef = tradeRef;
        this.eventType = eventType;
        this.eventTimestamp = ts;
        this.actor = actor;
        this.beforeState = before;
        this.afterState = after;
    }

    public Long getId()              { return id; }
    public String getEventId()       { return eventId; }
    public String getTradeRef()      { return tradeRef; }
    public String getEventType()     { return eventType; }
    public Instant getEventTimestamp(){ return eventTimestamp; }
    public String getActor()         { return actor; }
    public String getBeforeState()   { return beforeState; }
    public String getAfterState()    { return afterState; }
}
