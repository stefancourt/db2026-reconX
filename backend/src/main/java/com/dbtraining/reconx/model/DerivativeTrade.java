package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * ============================================================================
 * TICKET-ADV022 — DerivativeTrade with Builder pattern
 *
 * WHAT:    Option/derivative trade — underlying, strike, expiry, optionType.
 * HOW:     Same builder pattern. notional() = strike * quantity in the
 *          trade's currency (simplified — real derivatives use delta-adjusted).
 * ============================================================================
 */
public final class DerivativeTrade implements TradeType {

    public enum OptionType { CALL, PUT }

    private final TradeRef tradeRef;
    private final String underlying;
    private final BigDecimal strike;
    private final BigDecimal quantity;
    private final LocalDate expiry;
    private final OptionType optionType;
    private final Currency currency;
    private final Side side;
    private final LocalDate tradeDate;
    private final long counterpartyId;

    private DerivativeTrade(Builder b) {
        this.tradeRef       = b.tradeRef;
        this.underlying     = b.underlying;
        this.strike         = b.strike;
        this.quantity       = b.quantity;
        this.expiry         = b.expiry;
        this.optionType     = b.optionType;
        this.currency       = b.currency;
        this.side           = b.side;
        this.tradeDate      = b.tradeDate;
        this.counterpartyId = b.counterpartyId;
    }

    public static Builder builder() { return new Builder(); }

    @Override public TradeRef tradeRef()     { return tradeRef; }
    @Override public LocalDate tradeDate()   { return tradeDate; }
    @Override public AssetClass assetClass() { return AssetClass.DERIVATIVE; }

    /** Simplified notional = strike * quantity in the trade currency. */
    @Override public Money notional() {
        // TODO(TICKET-ADV022): return new Money(strike * quantity, currency).
        throw new UnsupportedOperationException("TICKET-ADV022");
    }

    public String underlying()       { return underlying; }
    public BigDecimal strike()       { return strike; }
    public BigDecimal quantity()     { return quantity; }
    public LocalDate expiry()        { return expiry; }
    public OptionType optionType()   { return optionType; }
    public Currency currency()       { return currency; }
    public Side side()               { return side; }
    public long counterpartyId()     { return counterpartyId; }

    @Override public boolean equals(Object o) {
        // TODO(TICKET-ADV028): pattern-match on DerivativeTrade and compare tradeRef.
        throw new UnsupportedOperationException("TICKET-ADV028");
    }
    @Override public int hashCode() {
        // TODO(TICKET-ADV028): hash from tradeRef.
        throw new UnsupportedOperationException("TICKET-ADV028");
    }

    @Override public String toString() {
        // TODO(TICKET-ADV030): "DerivativeTrade[ref=..., TYPE UNDERLYING on date, strike=... CCY, qty=..., expiry=..., side=...]"
        throw new UnsupportedOperationException("TICKET-ADV030");
    }

    public static final class Builder {
        private TradeRef tradeRef;
        private String underlying;
        private BigDecimal strike, quantity;
        private LocalDate expiry, tradeDate;
        private OptionType optionType;
        private Currency currency;
        private Side side;
        private long counterpartyId;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder underlying(String v)        { this.underlying = v; return this; }
        public Builder strike(BigDecimal v)        { this.strike = v; return this; }
        public Builder quantity(BigDecimal v)      { this.quantity = v; return this; }
        public Builder expiry(LocalDate v)         { this.expiry = v; return this; }
        public Builder optionType(OptionType v)    { this.optionType = v; return this; }
        public Builder currency(String code)       { this.currency = Currency.getInstance(code); return this; }
        public Builder side(Side v)                { this.side = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder counterpartyId(long v)      { this.counterpartyId = v; return this; }

        public DerivativeTrade build() {
            // TODO(TICKET-ADV022):
            //   - Objects.requireNonNull each required field.
            //   - strike and quantity must be > 0.
            //   - expiry must not be before tradeDate.
            //   - return new DerivativeTrade(this).
            throw new UnsupportedOperationException("TICKET-ADV022");
        }
    }
}
