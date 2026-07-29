package com.dbtraining.reconx.domain;

/**
 * Lifecycle status of a persisted {@link Trade}.
 *
 * <p>Values come from the schema: {@code trades.status} defaults to {@code PENDING}
 * (002-schema.xml), and {@code mv_daily_recon_summary} buckets rows by
 * {@code MATCHED} versus {@code UNMATCHED}/{@code DISPUTED} (005-mat-views.xml).
 * Stored as {@code @Enumerated(EnumType.STRING)} — the constant names are the
 * on-disk values.
 */
public enum TradeStatus {
    /** Booked, not yet reconciled. */
    PENDING,
    /** Reconciled against the counterparty feed with no discrepancy. */
    MATCHED,
    /** Reconciled and no counterparty trade was found. */
    UNMATCHED,
    /** Reconciled, a counterparty trade exists, but the values disagree. */
    DISPUTED
}
