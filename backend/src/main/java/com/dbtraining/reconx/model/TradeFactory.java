package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * ============================================================================
 * TICKET-ADV023 — TradeFactory: build a TradeType by asset-class string
 *
 * WHAT:    Single entry point that takes an asset-class string + a map of
 *          field values and returns the right TradeType impl.
 * HOW:     Switch on the asset-class string, dispatch to the correct
 *          builder. Map values are cast/parsed per asset class.
 * WHY:     The Kafka consumer + REST POST endpoint both need to convert an
 *          untyped payload into a typed TradeType. Centralising the
 *          construction here means the parsing logic lives in one place.
 * OBSERVE: TradeFactoryTest.create_unknownAssetClass_throws fails when a
 *          new TradeType impl is added without updating the switch.
 * HINT:    Sealed hierarchy guarantees that every concrete TradeType MUST be
 *          listed in TradeType.permits — so this switch can be made
 *          exhaustive over assetClass enum.
 * ============================================================================
 */
public final class TradeFactory {

    private TradeFactory() { }

    /**
     * TODO(TICKET-ADV023):
     *   1. Parse assetClass string into TradeType.AssetClass enum (toUpperCase first).
     *   2. switch on the enum and dispatch to the matching equity/fx/bond/derivative
     *      helper below.
     *   3. The switch must be exhaustive — every TradeType.AssetClass case handled.
     */
    public static TradeType create(String assetClass, Map<String, Object> p) {
        TradeType.AssetClass ac = TradeType.AssetClass.valueOf(assetClass.toUpperCase());
        return switch (ac) {
            case EQUITY     -> equity(p);
            case FX         -> fx(p);
            case BOND       -> bond(p);
            case DERIVATIVE -> derivative(p);
        };
    }

    /**
     * TODO(TICKET-ADV023):
     *   Build an EquityTrade from the map. Expected keys: tradeRef, symbol,
     *   quantity, price, currency, side, tradeDate, counterpartyId.
     */
    private static EquityTrade equity(Map<String, Object> p) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .instrumentSymbol((String) p.get("symbol"))
                .quantity(new BigDecimal(p.get("quantity").toString()))
                .price(new BigDecimal(p.get("price").toString()))
                .currency((String) p.get("currency"))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }

    /**
     * TODO(TICKET-ADV023):
     *   Build an FXTrade from the map. Expected keys: tradeRef, ccy1, ccy2,
     *   notionalCcy1, fxRate, side, tradeDate, counterpartyId.
     */
    private static FXTrade fx(Map<String, Object> p) {
        return FXTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .ccy1((String) p.get("ccy1"))
                .ccy2((String) p.get("ccy2"))
                .notionalCcy1(new BigDecimal(p.get("notionalCcy1").toString()))
                .fxRate(new BigDecimal(p.get("fxRate").toString()))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }

    /**
     * TODO(TICKET-ADV023):
     *   Build a BondTrade from the map. Expected keys: tradeRef, isin,
     *   faceValue, couponRate, maturityDate, currency, side, tradeDate,
     *   counterpartyId.
     */
    private static BondTrade bond(Map<String, Object> p) {
        return BondTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .isin((String) p.get("isin"))
                .faceValue(new BigDecimal(p.get("faceValue").toString()))
                .couponRate(new BigDecimal(p.get("couponRate").toString()))
                .maturityDate(LocalDate.parse((String) p.get("maturityDate")))
                .currency((String) p.get("currency"))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }

    /**
     * TODO(TICKET-ADV023):
     *   Build a DerivativeTrade from the map. Expected keys: tradeRef,
     *   underlying, strike, quantity, expiry, optionType, currency, side,
     *   tradeDate, counterpartyId.
     */
    private static DerivativeTrade derivative(Map<String, Object> p) {
        return DerivativeTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .underlying((String) p.get("underlying"))
                .strike(new BigDecimal(p.get("strike").toString()))
                .quantity(new BigDecimal(p.get("quantity").toString()))
                .expiry(LocalDate.parse((String) p.get("expiry")))
                .optionType(DerivativeTrade.OptionType.valueOf((String) p.get("optionType")))
                .currency((String) p.get("currency"))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }
}
