package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * ============================================================================
 * TICKET-ADV021 — BondTrade with Builder pattern
 *
 * WHAT:    Fixed-income trade — couponRate, maturityDate, faceValue, isin.
 * HOW:     Same builder pattern. notional() = faceValue (in the bond's ccy).
 * WHY:     Bonds need couponRate/maturity for downstream cashflow modelling.
 *          Modelling them on the trade is the simplest path for the demo.
 * ============================================================================
 */
public final class BondTrade implements TradeType {

    private final TradeRef tradeRef;
    private final String isin;
    private final BigDecimal faceValue;
    private final BigDecimal couponRate;
    private final LocalDate maturityDate;
    private final Currency currency;
    private final Side side;
    private final LocalDate tradeDate;
    private final long counterpartyId;

    private BondTrade(Builder b) {
        this.tradeRef       = b.tradeRef;
        this.isin           = b.isin;
        this.faceValue      = b.faceValue;
        this.couponRate     = b.couponRate;
        this.maturityDate   = b.maturityDate;
        this.currency       = b.currency;
        this.side           = b.side;
        this.tradeDate      = b.tradeDate;
        this.counterpartyId = b.counterpartyId;
    }

    public static Builder builder() { return new Builder(); }

    @Override public TradeRef tradeRef()     { return tradeRef; }
    @Override public LocalDate tradeDate()   { return tradeDate; }
    @Override public AssetClass assetClass() { return AssetClass.BOND; }

    /** Notional = faceValue in the bond's currency. */
    @Override public Money notional() {
        return new Money(faceValue, currency);
    }

    public String isin()              { return isin; }
    public BigDecimal faceValue()     { return faceValue; }
    public BigDecimal couponRate()    { return couponRate; }
    public LocalDate maturityDate()   { return maturityDate; }
    public Currency currency()        { return currency; }
    public Side side()                { return side; }
    public long counterpartyId()      { return counterpartyId; }

    @Override public boolean equals(Object o) {
        // TODO(TICKET-ADV028): pattern-match on BondTrade and compare tradeRef.
        throw new UnsupportedOperationException("TICKET-ADV028");
    }
    @Override public int hashCode() {
        // TODO(TICKET-ADV028): hash from tradeRef.
        throw new UnsupportedOperationException("TICKET-ADV028");
    }

    @Override public String toString() {
        // TODO(TICKET-ADV030): "BondTrade[ref=..., isin=..., face=... CCY, coupon=..., maturity=..., side=...]"
        throw new UnsupportedOperationException("TICKET-ADV030");
    }

    public static final class Builder {
        private TradeRef tradeRef;
        private String isin;
        private BigDecimal faceValue, couponRate;
        private LocalDate maturityDate, tradeDate;
        private Currency currency;
        private Side side;
        private long counterpartyId;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder isin(String v)              { this.isin = v; return this; }
        public Builder faceValue(BigDecimal v)     { this.faceValue = v; return this; }
        public Builder couponRate(BigDecimal v)    { this.couponRate = v; return this; }
        public Builder maturityDate(LocalDate v)   { this.maturityDate = v; return this; }
        public Builder currency(String code)       { this.currency = Currency.getInstance(code); return this; }
        public Builder side(Side v)                { this.side = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder counterpartyId(long v)      { this.counterpartyId = v; return this; }

        public BondTrade build() {
            Objects.requireNonNull(tradeRef,     "tradeRef");
            Objects.requireNonNull(isin,         "isin");
            Objects.requireNonNull(faceValue,    "faceValue");
            Objects.requireNonNull(couponRate,   "couponRate");
            Objects.requireNonNull(maturityDate, "maturityDate");
            Objects.requireNonNull(currency,     "currency");
            Objects.requireNonNull(side,         "side");
            Objects.requireNonNull(tradeDate,    "tradeDate");
            if (maturityDate.isBefore(tradeDate))
                throw new IllegalStateException("maturityDate cannot be before tradeDate");
            return new BondTrade(this);
        }
    }
}
