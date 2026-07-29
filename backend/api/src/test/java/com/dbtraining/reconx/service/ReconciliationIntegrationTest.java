package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.EquityTrade;
import com.dbtraining.reconx.model.ReconciliationRule;
import com.dbtraining.reconx.model.Side;
import com.dbtraining.reconx.model.TradeRef;
import com.dbtraining.reconx.model.TradeType;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.ReconBreakRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.domain.AssetClass;
import com.dbtraining.reconx.domain.Counterparty;
import com.dbtraining.reconx.domain.TradeStatus;
import com.dbtraining.reconx.domain.Instrument;
import com.dbtraining.reconx.domain.ReconBreak;
import com.dbtraining.reconx.domain.Trade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV044 — Testcontainers PostgreSQL wired into the Spring test context
 * TICKET-ADV045 — Integration test: insert -> recon -> verify
 *
 * WHAT:    Boots the same postgres:16-alpine image the compose stack runs,
 *          lets Liquibase build the schema against it, then drives a real
 *          insert / reconcile / persist-break round trip.
 * HOW:     @Testcontainers manages the container lifecycle, a static
 *          @DynamicPropertySource hands Spring the JDBC coordinates before
 *          the datasource is created.
 * WHY:     Mocks prove the code path; only real SQL proves the mapping. The
 *          container is what Day 6's Liquibase migrations run against in CI.
 * OBSERVE: `docker ps` during the run shows postgres:16-alpine; after the
 *          class finishes the container is gone.
 *
 * NOTE:    The student guide's reference solution names InternalTradeRepository,
 *          ExternalTradeRepository, ReconResultRepository and
 *          ReconciliationService — none of those exist in this codebase. The
 *          real shape is one `trades` table (TradeRepository), a
 *          ReconciliationEngine returning ReconResult DTOs, and a
 *          `recon_breaks` table (ReconBreakRepository) for the durable breaks.
 *          The external feed is modelled as inbound domain objects, which is
 *          how a counterparty file actually arrives.
 *
 * STATUS:  containerIsRunning (ADV044) passes today.
 *          insertedTradesAreReconciledAndPersisted (ADV045) is RED on purpose —
 *          it fails with UnsupportedOperationException("TICKET-ADV033") until
 *          ReconciliationEngine.reconcile / matchOne / priceQty are written.
 *          Treat it as the executable spec for ADV018 + ADV033.
 * ============================================================================
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ReconciliationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconx")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TradeRepository tradeRepo;
    @Autowired ReconBreakRepository reconBreakRepo;
    @Autowired InstrumentRepository instrumentRepo;
    @Autowired CounterpartyRepository counterpartyRepo;
    @Autowired ReconciliationEngine engine;

    /** TICKET-ADV044 — sanity: the container started and Spring wired up against it. */
    @Test
    void containerIsRunning() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(postgres.getJdbcUrl()).startsWith("jdbc:postgresql://");
        // Liquibase ran: the seeded reference tables are queryable.
        assertThat(instrumentRepo.count()).isPositive();
        assertThat(counterpartyRepo.count()).isPositive();
        assertThat(tradeRepo.count()).isNotNegative();
    }

    /** TICKET-ADV045 — insert trades, reconcile them, verify the persisted breaks. */
    @Test
    @Transactional
    void insertedTradesAreReconciledAndPersisted() {
        // given — two trades saved through the real repository against real Postgres
        Instrument instrument = instrumentRepo.findAll().getFirst();
        Counterparty counterparty = counterpartyRepo.findAll().getFirst();

        Trade matching = saveTrade("EQU-20260603-9001", instrument, counterparty,
                new BigDecimal("100.0000"), new BigDecimal("245.5000"));
        Trade mismatched = saveTrade("EQU-20260603-9002", instrument, counterparty,
                new BigDecimal("50.0000"), new BigDecimal("310.0000"));
        tradeRepo.flush();

        // read the internal book back out of the database — this is the round trip
        List<TradeType> internal = List.of(
                toDomain(tradeRepo.findByTradeRef("EQU-20260603-9001").orElseThrow()),
                toDomain(tradeRepo.findByTradeRef("EQU-20260603-9002").orElseThrow()));

        // the counterparty feed: one identical trade, one at a different price
        List<TradeType> external = List.of(
                domainTrade("EQU-20260603-9001", instrument.getSymbol(),
                        new BigDecimal("100.0000"), new BigDecimal("245.5000"), counterparty.getId()),
                domainTrade("EQU-20260603-9002", instrument.getSymbol(),
                        new BigDecimal("50.0000"), new BigDecimal("311.7500"), counterparty.getId()));

        // when
        List<ReconResult> results = engine.reconcile(internal, external, ReconciliationRule.EXACT);

        // then — one clean match, one priced break
        assertThat(results).hasSize(2);
        assertThat(results).filteredOn(r -> r.status() == ReconResult.Status.MATCHED)
                .singleElement()
                .extracting(ReconResult::tradeRef).isEqualTo("EQU-20260603-9001");

        ReconResult breakRow = results.stream()
                .filter(r -> r.status() == ReconResult.Status.BREAK)
                .findFirst().orElseThrow();
        assertThat(breakRow.tradeRef()).isEqualTo("EQU-20260603-9002");
        assertThat(breakRow.discrepancyType()).isEqualTo("VALUE_MISMATCH");

        // and — the break survives a write/read cycle through recon_breaks
        long breaksBefore = reconBreakRepo.count();
        ReconBreak persisted = new ReconBreak();
        persisted.setTradeId(mismatched.getId());
        persisted.setDiscrepancyType(breakRow.discrepancyType());
        reconBreakRepo.saveAndFlush(persisted);

        assertThat(reconBreakRepo.count()).isEqualTo(breaksBefore + 1);
        ReconBreak fetched = reconBreakRepo.findById(persisted.getId()).orElseThrow();
        assertThat(fetched.getTradeId()).isEqualTo(mismatched.getId());
        assertThat(fetched.getDiscrepancyType()).isEqualTo("VALUE_MISMATCH");
        assertThat(fetched.getStatus()).isEqualTo("OPEN");

        // sanity that `matching` really did land in the database
        assertThat(matching.getId()).isNotNull();
    }

    private Trade saveTrade(String ref, Instrument instrument, Counterparty counterparty,
                            BigDecimal qty, BigDecimal price) {
        Trade t = new Trade();
        t.setTradeRef(ref);
        t.setInstrument(instrument);
        t.setCounterparty(counterparty);
        t.setAssetClass(AssetClass.EQUITY);
        t.setSide(Side.BUY);
        t.setQuantity(qty);
        t.setPrice(price);
        t.setTradeDate(LocalDate.of(2026, 6, 3));
        t.setStatus(TradeStatus.PENDING);
        return tradeRepo.save(t);
    }

    /** Entity -> domain. The sealed TradeType hierarchy is what the engine speaks. */
    private TradeType toDomain(Trade t) {
        return domainTrade(t.getTradeRef(), t.getInstrument().getSymbol(),
                t.getQuantity(), t.getPrice(), t.getCounterparty().getId());
    }

    private TradeType domainTrade(String ref, String symbol, BigDecimal qty,
                                  BigDecimal price, long counterpartyId) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol(symbol)
                .quantity(qty)
                .price(price)
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(counterpartyId)
                .build();
    }
}
