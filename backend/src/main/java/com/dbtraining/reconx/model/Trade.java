  package com.dbtraining.reconx.model;    
                                                                              
  import java.time.LocalDate;                                                 
  import java.util.Objects;                                                   
                                                                              
  abstract sealed class Trade permits EquityTrade, FXTrade, BondTrade,        
  DerivativeTrade {                              

      private final TradeRef tradeRef;
      private final Money notional;
      private final LocalDate tradeDate;

      protected Trade(TradeRef tradeRef, Money notional, LocalDate tradeDate)
    {
        this.tradeRef = Objects.requireNonNull(tradeRef, "tradeRef must not be  null");                                        
        this.notional = Objects.requireNonNull(notional, "notional must not be null");
        this.tradeDate = Objects.requireNonNull(tradeDate, "tradeDate must not be null");
    }

    public TradeRef tradeRef(){
        return this.tradeRef;
    }

    public Money notional(){
        return this.notional;
    }

    public LocalDate tradeDate(){
        return this.tradeDate;
    }
  }