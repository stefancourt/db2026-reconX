package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.EquityTrade;
import com.dbtraining.reconx.model.Side;
import com.dbtraining.reconx.model.TradeRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TradeAnalyticsTest {

    @Test
    void testNotionalByCounterparty() {
        TradeAnalyticsService service = new TradeAnalyticsService();

        EquityTrade trade1 = EquityTrade.builder()
                .tradeRef(new TradeRef("DBB-20260727-0001"))
                .counterpartyId(1L)
                .instrumentSymbol("AAPL")
                .currency("USD")
                .tradeDate(LocalDate.now())
                .side(Side.BUY)
                .price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("5.00"))
                .build();

        EquityTrade trade2 = EquityTrade.builder()
                .tradeRef(new TradeRef("DBB-20260727-0002"))
                .counterpartyId(1L)
                .instrumentSymbol("AAPL")
                .currency("USD")
                .tradeDate(LocalDate.now())
                .side(Side.BUY)
                .price(new BigDecimal("20.00"))
                .quantity(new BigDecimal("2.00"))
                .build();

        Map<Long, TradeAnalyticsService.NotionalSummary> result = 
                service.notionalByCounterparty(List.of(trade1, trade2));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(2, result.get(1L).count());
        assertEquals(new BigDecimal("90.0000"), result.get(1L).total());
    }

    @Test
    void testVwapAndPnlByInstrument() {
        TradeAnalyticsService service = new TradeAnalyticsService();

        EquityTrade trade1 = EquityTrade.builder()
                .tradeRef(new TradeRef("DBB-20260727-0003"))
                .counterpartyId(1L)
                .instrumentSymbol("MSFT")
                .currency("USD")
                .tradeDate(LocalDate.now())
                .side(Side.BUY)
                .price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("10.00"))
                .build();

        EquityTrade trade2 = EquityTrade.builder()
                .tradeRef(new TradeRef("DBB-20260727-0004"))
                .counterpartyId(1L)
                .instrumentSymbol("MSFT")
                .currency("USD")
                .tradeDate(LocalDate.now())
                .side(Side.SELL)
                .price(new BigDecimal("20.00"))
                .quantity(new BigDecimal("20.00"))
                .build();

        Map<String, BigDecimal> vwapMap = service.vwapByInstrument(List.of(trade1, trade2));
        Map<String, BigDecimal> pnlMap = service.pnlByInstrument(List.of(trade1, trade2));

        assertNotNull(vwapMap);
        assertNotNull(pnlMap);
        
        assertEquals(0, new BigDecimal("16.666667").compareTo(vwapMap.get("MSFT")));
        assertEquals(0, new BigDecimal("300.00").compareTo(pnlMap.get("MSFT")));
    }
}