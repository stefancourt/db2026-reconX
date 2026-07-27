package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.BondTrade;
import com.dbtraining.reconx.model.DerivativeTrade;
import com.dbtraining.reconx.model.EquityTrade;
import com.dbtraining.reconx.model.FXTrade;
import com.dbtraining.reconx.model.Side;
import com.dbtraining.reconx.model.TradeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * TICKET-ADV034 — Trade analytics with Collectors (groupingBy + summarizing)
 * TICKET-ADV035 — VWAP calculator using Streams + custom collector
 * TICKET-ADV036 — P&L per instrument: stream reduction
 * ============================================================================
 */
@Service
public class TradeAnalyticsService {

    /** TICKET-ADV034 — count + sum of notional per counterparty. */
    public Map<Long, NotionalSummary> notionalByCounterparty(List<? extends TradeType> trades) {
        if (trades == null || trades.isEmpty()) return Map.of();
        return trades.stream().collect(Collectors.groupingBy(
                this::counterpartyIdOf,
                Collectors.collectingAndThen(Collectors.toList(), list -> new NotionalSummary(
                        list.size(),
                        list.stream()
                                .map(t -> t.notional().amount())
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))));
    }

    /**
     * TICKET-ADV035 — VWAP = SUM(price * qty) / SUM(qty). Equity-only — only
     * EquityTrade has a meaningful price-volume pair.
     */
    public Map<String, BigDecimal> vwapByInstrument(List<EquityTrade> equityTrades) {
        if (equityTrades == null || equityTrades.isEmpty()) return Map.of();
        return equityTrades.stream().collect(Collectors.groupingBy(
                EquityTrade::instrumentSymbol,
                Collectors.collectingAndThen(Collectors.toList(), bucket -> {
                    BigDecimal totalPxQty = BigDecimal.ZERO;
                    BigDecimal totalQty   = BigDecimal.ZERO;
                    for (EquityTrade t : bucket) {
                        totalPxQty = totalPxQty.add(t.price().multiply(t.quantity()));
                        totalQty   = totalQty.add(t.quantity());
                    }
                    return totalQty.signum() == 0
                            ? BigDecimal.ZERO
                            : totalPxQty.divide(totalQty, 6, RoundingMode.HALF_UP);
                })));
    }

    /** TICKET-ADV036 — P&L per instrument symbol (sign by Side). */
    public Map<String, BigDecimal> pnlByInstrument(List<EquityTrade> equityTrades) {
        if (equityTrades == null || equityTrades.isEmpty()) return Map.of();
        return equityTrades.stream().collect(Collectors.groupingBy(
                EquityTrade::instrumentSymbol,
                Collectors.mapping(this::pnl,
                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }

    private BigDecimal pnl(EquityTrade t) {
        BigDecimal abs = t.price().multiply(t.quantity());
        return t.side() == Side.SELL ? abs : abs.negate();
    }

    private long counterpartyIdOf(TradeType t) {
        return switch (t) {
            case EquityTrade e     -> e.counterpartyId();
            case FXTrade fx        -> fx.counterpartyId();
            case BondTrade b       -> b.counterpartyId();
            case DerivativeTrade d -> d.counterpartyId();
        };
    }

    public record NotionalSummary(long count, BigDecimal total) {}
}
