package com.dbtraining.reconx.dto;

/**
 * TICKET-ADV033 — Output row from ReconciliationEngine.reconcile().
 *
 * Status drives whether the row becomes a recon_break or is logged as matched.
 */
public record ReconResult(
        String tradeRef,
        Status status,
        String discrepancyType,
        String details
) {
    public enum Status { MATCHED, BREAK }

    public static ReconResult matched(String tradeRef) {
        return new ReconResult(tradeRef, Status.MATCHED, null, null);
    }

    public static ReconResult breakResult(String tradeRef, String discrepancyType, String details) {
        return new ReconResult(tradeRef, Status.BREAK, discrepancyType, details);
    }
}
