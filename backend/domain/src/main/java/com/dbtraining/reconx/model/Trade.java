  package com.dbtraining.reconx.model;    
                                                                              
  import java.time.LocalDate;                                                 
  import java.util.Objects;                                                   
                                                                              
/**
 * Abstract base for all concrete trade types.
 *
 * WHAT: Holds tradeRef, notional, and tradeDate common to every trade.
 * HOW:  Abstract sealed class; only the four permitted subtypes extend it.
 * WHY:  Avoids repeating the same three fields and null-checks in every subclass.
 */
abstract sealed class Trade permits EquityTrade, FXTrade, BondTrade, DerivativeTrade {

    private final TradeRef tradeRef;
    private final Money notional;
    private final LocalDate tradeDate;

    /**
     * @param tradeRef  natural key, must not be {@code null}
     * @param notional  monetary value, must not be {@code null}
     * @param tradeDate business date, must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    protected Trade(TradeRef tradeRef, Money notional, LocalDate tradeDate) {
        this.tradeRef  = Objects.requireNonNull(tradeRef,  "tradeRef must not be null");
        this.notional  = Objects.requireNonNull(notional,  "notional must not be null");
        this.tradeDate = Objects.requireNonNull(tradeDate, "tradeDate must not be null");
    }

    /** @return the natural key that uniquely identifies this trade */
    public TradeRef tradeRef() { return this.tradeRef; }

    /** @return the notional value, never {@code null} */
    public Money notional() { return this.notional; }

    /** @return the business date the trade was struck on, never {@code null} */
    public LocalDate tradeDate() { return this.tradeDate; }
}