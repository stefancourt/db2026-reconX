package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV047 — Edge cases: empty/single/all-mismatched inputs handled.
 */
class ReconciliationEngineEdgeCasesTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    // -------------------------------------------------------------------------
    // Edge case 1: empty internal list → returns empty result, no exception
    // -------------------------------------------------------------------------

    @Test
    void reconcile_emptyInternalList_returnsEmpty() {
        // given
        List<TradeType> internal = List.of();
        List<TradeType> external = List.of(equity("EQU-20260603-0001", "100.00", "1000"));

        // when
        List<ReconResult> out = engine.reconcile(internal, external, ReconciliationRule.EXACT);

        // then
        assertThat(out).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Edge case 2: single internal trade, no external feed → one BREAK
    // -------------------------------------------------------------------------

    @Test
    void reconcile_singleInternalNoExternal_returnsBreak() {
        // given
        List<TradeType> internal = List.of(equity("EQU-20260603-0001", "100.00", "1000"));
        List<TradeType> external = List.of();

        // when
        List<ReconResult> out = engine.reconcile(internal, external, ReconciliationRule.EXACT);

        // then
        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.BREAK);
        assertThat(out.get(0).discrepancyType()).isEqualTo("MISSING_EXTERNAL");
    }

    // -------------------------------------------------------------------------
    // Edge case 3: all trades mismatched → three BREAKs, summary matched=0
    // -------------------------------------------------------------------------

    @Test
    void reconcile_allMismatched_returnsAllBreaks() {
        // given — internal prices differ from external prices, outside EXACT tolerance
        List<TradeType> internals = List.of(
                equity("EQU-20260603-0001", "100.00", "1000"),
                equity("EQU-20260603-0002", "200.00", "1000"),
                equity("EQU-20260603-0003", "300.00", "1000"));

        List<TradeType> externals = List.of(
                equity("EQU-20260603-0001", "110.00", "1000"),
                equity("EQU-20260603-0002", "220.00", "1000"),
                equity("EQU-20260603-0003", "330.00", "1000"));

        // when
        List<ReconResult> out = engine.reconcile(internals, externals, ReconciliationRule.EXACT);

        // then
        ReconSummary summary = out.stream().collect(new ReconSummaryCollector());
        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.matched()).isEqualTo(0);
        assertThat(summary.broken()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private EquityTrade equity(String ref, String price, String qty) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol("SAP.DE")
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }
}
