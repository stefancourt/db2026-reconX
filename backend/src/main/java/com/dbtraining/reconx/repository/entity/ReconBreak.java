package com.dbtraining.reconx.repository.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * TICKET-ADV070 — Recon break record. Status transitions: OPEN -> RESOLVED.
 * Exposed via PUT /api/v1/recon/results/{id}/resolve.
 */
@Entity
@Table(name = "recon_breaks")
public class ReconBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

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

    public Long getId()                { return id; }
    public Long getTradeId()           { return tradeId; }
    public String getDiscrepancyType() { return discrepancyType; }
    public String getStatus()          { return status; }
    public Instant getDetectedAt()     { return detectedAt; }
    public Instant getResolvedAt()     { return resolvedAt; }
    public String getResolutionNote()  { return resolutionNote; }

    public void setTradeId(Long v)              { this.tradeId = v; }
    public void setDiscrepancyType(String v)    { this.discrepancyType = v; }

    public void resolve(String note) {
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
        this.resolutionNote = note;
    }
}
