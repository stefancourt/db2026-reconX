package com.dbtraining.reconx.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * TICKET-ADV024 — Immutable value object: TradeRef (natural key for a trade)
 *
 * WHAT:    Strongly-typed wrapper around the trade reference string. Format:
 *          AAA-YYYYMMDD-NNNN  (3 letters, 8-digit date, 4 digits).
 * HOW:     Compact constructor validates against the regex; null and bad
 *          formats fail at construction.
 * WHY:     A bare String "trade reference" can be confused with any other
 *          String — counterparty name, instrument symbol. TradeRef as a
 *          distinct type makes those mix-ups a compile error.
 * OBSERVE: TradeRef.of("EQU-20260602-0001") works; .of("foo") throws.
 * ============================================================================
 */
public record TradeRef(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z]{3}-\\d{8}-\\d{4}$");

    public TradeRef {
        Objects.requireNonNull(value, "tradeRef value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tradeRef format '%s' — expected AAA-YYYYMMDD-NNNN".formatted(value));
        }
    }

    public static TradeRef of(String value) {
        return new TradeRef(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
