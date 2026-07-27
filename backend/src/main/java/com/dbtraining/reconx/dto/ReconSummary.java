package com.dbtraining.reconx.dto;

/**
 * Data Transfer Object representing the summary of a reconciliation run.
 */
public record ReconSummary(
        long total,
        long matched,
        long broken
) {
}
