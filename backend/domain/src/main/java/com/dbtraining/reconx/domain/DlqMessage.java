package com.dbtraining.reconx.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TICKET-ADV136 — one row per message that exhausted its retries and landed on
 * a `*-dlq` topic. Written by DlqConsumer, read and deleted by the admin
 * replay endpoint.
 *
 * <p>{@code partition} and {@code offset} are SQL reserved words, so the columns
 * are named {@code dlq_partition} / {@code record_offset}. Payload is kept as the
 * raw JSON string rather than an embedded object: the whole point of a DLQ row is
 * to survive the entity refactor that may have caused the failure in the first
 * place.
 */
@Entity
@Table(name = "dlq_messages")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "trade_ref", length = 30)
    private String tradeRef;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;

    @Column(name = "dlq_partition", nullable = false)
    private Integer partition;

    @Column(name = "record_offset", nullable = false)
    private Long offset;

    // Liquibase renders type="CLOB" as TEXT on Postgres — match it explicitly so
    // Hibernate ddl-auto: validate does not complain about a type mismatch.
    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;
}
