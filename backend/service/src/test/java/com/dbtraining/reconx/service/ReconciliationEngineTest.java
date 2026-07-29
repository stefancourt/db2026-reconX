package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV040 / ADV041 / ADV042 — TDD: write the test FIRST, then the impl.
 */
class ReconciliationEngineTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    @Test
    void testReconcile_exactMatch_returnsMatched() {
        EquityTrade internal = equity("EQU-20260603-0001", "100.00", "1000");                                                                                                                        
        EquityTrade external = equity("EQU-20260603-0001", "100.00", "1000");     
                                                                                                                                                                                                     
        // when                                                                                                                                                                                      
        List<ReconResult> out = engine.reconcile(List.of(internal), List.of(external), ReconciliationRule.EXACT);                                                                                  

        // then
        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.MATCHED);

    }

    @Test
    void testReconcile_priceTolerance_withinThreshold() {
        // TODO(TICKET-ADV041): prices 100.00 vs 100.50 + PRICE_TOLERANCE_1PCT rule -> status MATCHED.
        org.junit.jupiter.api.Assertions.fail("TICKET-ADV041 not implemented yet");
    }

    @Test
    void testReconcile_missingCounterpartyTrade_returnsBreak() {
        // TODO(TICKET-ADV042): internal trade with no external counterpart -> status BREAK,
        //                     discrepancyType = "MISSING_EXTERNAL".
        org.junit.jupiter.api.Assertions.fail("TICKET-ADV042 not implemented yet");
    }

    @Test
    void testReconcile_emptyInternal_returnsEmpty() {
        List<ReconResult> out = engine.reconcile(List.of(), List.of(), ReconciliationRule.EXACT);

        // then
        assertThat(out).isEmpty();
    }

    private EquityTrade equity(String ref, String price, String qty) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol("SAP.DE")
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .currency("EUR").side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }
}
