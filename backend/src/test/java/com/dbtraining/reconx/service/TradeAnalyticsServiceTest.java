package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.EquityTrade;
import com.dbtraining.reconx.model.Side;
import com.dbtraining.reconx.model.TradeRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** TICKET-ADV035 — VWAP via a custom Collector. */
class TradeAnalyticsServiceTest {

    private final TradeAnalyticsService service = new TradeAnalyticsService();

    @Test
    void vwap_matchesHandComputedValue() {
        // 100 @ 10.00 + 300 @ 12.00 = 1000 + 3600 = 4600 over 400 qty = 11.5000
        List<EquityTrade> trades = List.of(
                equity("EQU-20260603-0001", "SAP.DE", "100", "10.00"),
                equity("EQU-20260603-0002", "SAP.DE", "300", "12.00"));

        assertThat(service.vwap(trades)).isEqualByComparingTo(new BigDecimal("11.5000"));
    }

    @Test
    void vwap_roundsHalfUpToFourDecimals() {
        // 1 @ 1 + 2 @ 2 = 5 over 3 qty = 1.666666... -> 1.6667
        List<EquityTrade> trades = List.of(
                equity("EQU-20260603-0001", "SAP.DE", "1", "1"),
                equity("EQU-20260603-0002", "SAP.DE", "2", "2"));

        BigDecimal vwap = service.vwap(trades);

        assertThat(vwap).isEqualByComparingTo(new BigDecimal("1.6667"));
        assertThat(vwap.scale()).isEqualTo(4);
    }

    @Test
    void vwap_emptyInput_returnsZeroInsteadOfThrowing() {
        assertThat(service.vwap(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void vwap_serialAndParallelStreamsAgree() {
        List<EquityTrade> trades = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            trades.add(equity("EQU-20260603-%04d".formatted(i), "SAP.DE",
                    String.valueOf(i), "1" + (i % 97) + ".37"));
        }

        BigDecimal serial = trades.stream().collect(VwapCollector.toVwap());
        BigDecimal parallel = trades.parallelStream().collect(VwapCollector.toVwap());

        assertThat(parallel).isEqualByComparingTo(serial);
    }

    @Test
    void vwapByInstrument_groupsBySymbol() {
        List<EquityTrade> trades = List.of(
                equity("EQU-20260603-0001", "SAP.DE", "100", "10.00"),
                equity("EQU-20260603-0002", "SAP.DE", "300", "12.00"),
                equity("EQU-20260603-0003", "BMW.DE", "50", "80.00"));

        Map<String, BigDecimal> vwaps = service.vwapByInstrument(trades);

        assertThat(vwaps).containsOnlyKeys("SAP.DE", "BMW.DE");
        assertThat(vwaps.get("SAP.DE")).isEqualByComparingTo(new BigDecimal("11.5000"));
        assertThat(vwaps.get("BMW.DE")).isEqualByComparingTo(new BigDecimal("80.0000"));
    }

    @Test
    void vwapCollector_combinerReturnsFreshAccumulator() {
        VwapCollector collector = VwapCollector.toVwap();

        VwapCollector.Acc left = collector.supplier().get();
        collector.accumulator().accept(left, equity("EQU-20260603-0001", "SAP.DE", "100", "10.00"));
        VwapCollector.Acc right = collector.supplier().get();
        collector.accumulator().accept(right, equity("EQU-20260603-0002", "SAP.DE", "300", "12.00"));

        VwapCollector.Acc merged = collector.combiner().apply(left, right);

        assertThat(merged).isNotSameAs(left).isNotSameAs(right);
        assertThat(collector.finisher().apply(merged)).isEqualByComparingTo(new BigDecimal("11.5000"));
        // Inputs untouched: left still holds only its own trade.
        assertThat(collector.finisher().apply(left)).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void vwapCollector_isUnorderedOnly() {
        assertThat(VwapCollector.toVwap().characteristics())
                .containsExactly(java.util.stream.Collector.Characteristics.UNORDERED);
    }

    private EquityTrade equity(String ref, String symbol, String qty, String price) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol(symbol)
                .quantity(new BigDecimal(qty))
                .price(new BigDecimal(price))
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }
}
