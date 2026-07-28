package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.AssetClass;
import com.dbtraining.reconx.domain.Counterparty;
import com.dbtraining.reconx.domain.Instrument;
import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.domain.TradeStatus;
import com.dbtraining.reconx.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV055 — smoke test for the TradeRepository query surface.
 *
 * Runs against an in-memory H2 built from src/test/resources/schema.sql, because
 * the Liquibase changelog lives in the `api` module and this module sits below
 * it in the reactor. Fixtures are inserted here rather than read from the Day 1
 * CSV seed for the same reason.
 * ============================================================================
 */
@DataJpaTest
class TradeRepositoryTest {

    @Autowired private TradeRepository repo;
    @Autowired private TestEntityManager em;

    private Long cpAlphaId;
    private Long cpBetaId;

    private static final LocalDate D_2026_06_01 = LocalDate.of(2026, 6, 1);
    private static final LocalDate D_2026_06_10 = LocalDate.of(2026, 6, 10);
    private static final LocalDate D_2026_06_20 = LocalDate.of(2026, 6, 20);
    private static final LocalDate D_2026_07_05 = LocalDate.of(2026, 7, 5);

    @BeforeEach
    void seed() {
        Counterparty alpha = counterparty("Alpha Bank", "ALPHA00000000000001", "EMEA");
        Counterparty beta  = counterparty("Beta Securities", "BETA000000000000001", "APAC");
        Instrument vod     = instrument("VOD.L", "Vodafone Group plc", "GBP");

        cpAlphaId = alpha.getId();
        cpBetaId  = beta.getId();

        // in range, alpha, PENDING
        trade("EQU-20260610-0001", alpha, vod, D_2026_06_10, TradeStatus.PENDING);
        // in range, beta, MATCHED
        trade("EQU-20260620-0002", beta,  vod, D_2026_06_20, TradeStatus.MATCHED);
        // in range, alpha, MATCHED
        trade("EQU-20260615-0003", alpha, vod, LocalDate.of(2026, 6, 15), TradeStatus.MATCHED);
        // outside the range on both ends
        trade("EQU-20260501-0004", alpha, vod, LocalDate.of(2026, 5, 1), TradeStatus.PENDING);
        trade("EQU-20260705-0005", beta,  vod, D_2026_07_05, TradeStatus.PENDING);

        em.flush();
        em.clear();
    }

    @Test
    void findByTradeRefReturnsTheMatchingTrade() {
        Optional<Trade> found = repo.findByTradeRef("EQU-20260610-0001");

        assertThat(found).isPresent();
        assertThat(found.get().getTradeDate()).isEqualTo(D_2026_06_10);
        assertThat(found.get().getStatus()).isEqualTo(TradeStatus.PENDING);
    }

    @Test
    void findByTradeRefIsEmptyForAnUnknownRef() {
        assertThat(repo.findByTradeRef("NOPE-00000000-0000")).isEmpty();
    }

    /** The Done-when call: date range only, both optional filters null. */
    @Test
    void findByFiltersWithNullOptionalsReturnsEverythingInTheDateRange() {
        Page<Trade> page = repo.findByFilters(
                D_2026_06_01, D_2026_06_20, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(refsOf(page)).containsExactlyInAnyOrder(
                "EQU-20260610-0001", "EQU-20260620-0002", "EQU-20260615-0003");
    }

    @Test
    void findByFiltersNarrowsByStatus() {
        Page<Trade> page = repo.findByFilters(
                D_2026_06_01, D_2026_06_20, TradeStatus.MATCHED, null, PageRequest.of(0, 10));

        assertThat(refsOf(page)).containsExactlyInAnyOrder(
                "EQU-20260620-0002", "EQU-20260615-0003");
    }

    @Test
    void findByFiltersNarrowsByCounterparty() {
        Page<Trade> page = repo.findByFilters(
                D_2026_06_01, D_2026_06_20, null, cpAlphaId, PageRequest.of(0, 10));

        assertThat(refsOf(page)).containsExactlyInAnyOrder(
                "EQU-20260610-0001", "EQU-20260615-0003");
    }

    @Test
    void findByFiltersCombinesBothOptionalFilters() {
        Page<Trade> page = repo.findByFilters(
                D_2026_06_01, D_2026_06_20, TradeStatus.MATCHED, cpBetaId, PageRequest.of(0, 10));

        assertThat(refsOf(page)).containsExactly("EQU-20260620-0002");
    }

    @Test
    void findByFiltersPaginates() {
        var pageable = PageRequest.of(0, 2, Sort.by("tradeRef"));

        Page<Trade> first = repo.findByFilters(D_2026_06_01, D_2026_06_20, null, null, pageable);

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(refsOf(first)).containsExactly("EQU-20260610-0001", "EQU-20260615-0003");

        Page<Trade> second = repo.findByFilters(
                D_2026_06_01, D_2026_06_20, null, null, pageable.next());

        assertThat(refsOf(second)).containsExactly("EQU-20260620-0002");
    }

    @Test
    void countByStatusCountsAcrossTheWholeTable() {
        assertThat(repo.countByStatus(TradeStatus.PENDING)).isEqualTo(3);
        assertThat(repo.countByStatus(TradeStatus.MATCHED)).isEqualTo(2);
        assertThat(repo.countByStatus(TradeStatus.DISPUTED)).isZero();
    }

    // ------------------------------------------------------------------ fixtures

    private static List<String> refsOf(Page<Trade> page) {
        return page.getContent().stream().map(Trade::getTradeRef).toList();
    }

    private Counterparty counterparty(String name, String lei, String region) {
        Counterparty c = new Counterparty();
        c.setName(name);
        c.setLeiCode(lei);
        c.setRegion(region);
        return em.persistAndFlush(c);
    }

    private Instrument instrument(String symbol, String name, String ccy) {
        Instrument i = new Instrument();
        i.setSymbol(symbol);
        i.setName(name);
        i.setAssetClass(AssetClass.EQUITY);
        i.setCurrency(ccy);
        return em.persistAndFlush(i);
    }

    private void trade(String ref, Counterparty cp, Instrument ins, LocalDate date, TradeStatus status) {
        Trade t = new Trade();
        t.setTradeRef(ref);
        t.setCounterparty(cp);
        t.setInstrument(ins);
        t.setAssetClass(AssetClass.EQUITY);
        t.setSide(Side.BUY);
        t.setQuantity(new BigDecimal("100.0000"));
        t.setPrice(new BigDecimal("72.5000"));
        t.setTradeDate(date);
        t.setStatus(status);
        em.persist(t);
    }
}
