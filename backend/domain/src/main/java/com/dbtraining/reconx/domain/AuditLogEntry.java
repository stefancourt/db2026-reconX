package com.dbtraining.reconx.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * TICKET-ADV132 / ADV137 — Append-only audit row written by AuditEventConsumer.
 * Used for the event-sourcing rebuild of trade state (TICKET-ADV137).
 *
 * <p>Append-only, so {@code @Getter} and no setters at all. The all-args
 * constructor stays hand-written: Lombok's {@code @AllArgsConstructor} would
 * include the generated {@code id}, which no caller should ever supply.
 */
@Entity
@Table(name = "audit_log")
@Getter
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

    // No @Lob — Hibernate 6 + Postgres treats @Lob String as an OID column.
    //
    // columnDefinition = "TEXT" does not work either: the changelog declares these
    // as CLOB, which Liquibase renders as `text` on Postgres but as `clob` on H2, so
    // ddl-auto: validate failed on the dev profile with "wrong column type ... found
    // [character large object], but expecting [text]". Declaring the JDBC type instead
    // of a literal DDL string lets each dialect pick its own spelling — `text` on
    // Postgres, `clob` on H2 — and both match what Liquibase created.
    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(name = "before_state")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(name = "after_state")
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
}
