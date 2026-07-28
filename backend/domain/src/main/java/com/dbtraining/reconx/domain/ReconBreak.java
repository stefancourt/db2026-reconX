package com.dbtraining.reconx.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * TICKET-ADV070 — Recon break record. Status transitions: OPEN -> RESOLVED.
 * Exposed via PUT /api/v1/recon/results/{id}/resolve.
 *
 * <p>Only {@code tradeId} and {@code discrepancyType} get a {@code @Setter}.
 * {@code status}, {@code resolvedAt} and {@code resolutionNote} move together and
 * only through {@link #resolve(String)} — a generated {@code setStatus} would let a
 * caller mark a break RESOLVED with no timestamp and no note.
 */
@Entity
@Table(name = "recon_breaks")
@Getter
public class ReconBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    @Setter
    @Column(name = "discrepancy_type", nullable = false, length = 30)
    private String discrepancyType;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    public ReconBreak() {}

    public void resolve(String note) {
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
        this.resolutionNote = note;
    }
}
