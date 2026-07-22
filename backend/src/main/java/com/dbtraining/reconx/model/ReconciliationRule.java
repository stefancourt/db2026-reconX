package com.dbtraining.reconx.model;

import java.math.BigDecimal;

/**
 * ============================================================================
 * TICKET-ADV026 — ReconciliationRule enum with configurable thresholds
 *
 * WHAT:    Each enum value carries its own price tolerance (%) and quantity
 *          tolerance (absolute units). {@link #matches} returns true if the
 *          internal vs external trade pair is within tolerance.
 * HOW:     Enum-with-state pattern — instance fields + a behaviour method.
 * WHY:     Putting the rule on the enum keeps "what is a match" co-located
 *          with the rule's name, so the reconciliation engine is just:
 *          `if (rule.matches(internal, external)) ... matched ...`.
 * OBSERVE: PRICE_TOLERANCE_1PCT.matches(p, p*1.005) is true; *1.02 is false.
 * ============================================================================
 */
public enum ReconciliationRule {

    EXACT(BigDecimal.ZERO, BigDecimal.ZERO),
    PRICE_TOLERANCE_1PCT(new BigDecimal("0.01"), BigDecimal.ZERO),
    PRICE_TOLERANCE_50BPS(new BigDecimal("0.005"), BigDecimal.ZERO),
    QTY_TOLERANCE_5UNITS(BigDecimal.ZERO, new BigDecimal("5")),
    LOOSE(new BigDecimal("0.05"), new BigDecimal("10"));

    private final BigDecimal priceTolerancePct;
    private final BigDecimal qtyToleranceAbs;

    ReconciliationRule(BigDecimal priceTolerancePct, BigDecimal qtyToleranceAbs) {
        this.priceTolerancePct = priceTolerancePct;
        this.qtyToleranceAbs   = qtyToleranceAbs;
    }

    public BigDecimal priceTolerancePct() { return priceTolerancePct; }
    public BigDecimal qtyToleranceAbs()   { return qtyToleranceAbs; }

    /**
     * Decide whether two prices/quantities are within this rule's tolerance.
     * @return true if BOTH the price diff (as %) AND the qty diff (as abs)
     *         are within tolerance.
     */
    public boolean matches(BigDecimal internalPrice, BigDecimal internalQty,
                           BigDecimal externalPrice, BigDecimal externalQty) {
        // TODO(TICKET-ADV026):
        //   1. Compute |internalPrice - externalPrice| as priceDiff.
        //   2. priceDiffPct = priceDiff / internalPrice (guard divide-by-zero).
        //   3. qtyDiff = |internalQty - externalQty|.
        //   4. Return true iff priceDiffPct <= priceTolerancePct AND
        //      qtyDiff <= qtyToleranceAbs.
        throw new UnsupportedOperationException("TICKET-ADV026");
    }
}
