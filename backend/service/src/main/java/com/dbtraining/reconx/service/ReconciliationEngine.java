package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.ReconciliationRule;
import com.dbtraining.reconx.model.TradeType;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * TICKET-ADV033 — ReconciliationEngine using Streams (parallel matching)
 * TICKET-ADV037 — CompletableFuture: parallel recon by counterparty
 * TICKET-ADV047 — Edge cases: empty/single/all-mismatched inputs handled
 * TICKET-ADV084 — @Timed exports reconciliation_duration_seconds histogram
 *
 * WHAT:    Compares internal trades against external (counterparty) trades and
 *          returns a ReconResult per internal trade (MATCHED or BREAK).
 * HOW:     Index externals by tradeRef, then stream internals and look each
 *          up. CompletableFuture variant batches by counterparty for
 *          throughput on large books.
 * WHY:     This is the spine of the product. Everything else (REST API,
 *          Kafka consumers, dashboard) ultimately calls into here.
 * OBSERVE: Histogram appears at /actuator/prometheus under
 *          reconciliation_duration_seconds.
 * ============================================================================
 */
@Service
public class ReconciliationEngine {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEngine.class);

    public void scheduleRecon(String tradeRef) {
        log.debug("Scheduling reconciliation for tradeRef={}", tradeRef);
    }

    public void cancelPendingRecon(String tradeRef) {
        log.debug("Cancelling pending reconciliation for tradeRef={}", tradeRef);
    }

    @Timed(value = "reconciliation.duration", description = "Wall time of reconcile()",
           percentiles = {0.5, 0.95, 0.99}, histogram = true)
    public List<ReconResult> reconcile(List<TradeType> internal,
                                       List<TradeType> external,
                                       ReconciliationRule rule) {
        // TICKET-ADV047: guard against null/empty inputs
        if (internal == null || internal.isEmpty()) return List.of();
        List<TradeType> externalSafe = (external == null) ? List.of() : external;

        // index externals by tradeRef for O(1) lookups — (a, b) -> a handles duplicate refs
        Map<String, TradeType> externalByRef = externalSafe.stream()
                .collect(Collectors.toMap(t -> t.tradeRef().value(), Function.identity(), (a, b) -> a));

        return internal.parallelStream()
                .map(in -> matchOne(in, externalByRef.get(in.tradeRef().value()), rule))
                .toList();
    }

    /**
     * TICKET-ADV037 — split by counterparty, reconcile each batch concurrently,
     * combine into a single result list. Caller passes one external feed per
     * counterparty (typical real-world shape).
     */
    public CompletableFuture<List<ReconResult>> reconcileByCounterparty(
            Map<Long, List<TradeType>> internalByCp,
            Map<Long, List<TradeType>> externalByCp,
            ReconciliationRule rule) {
        List<CompletableFuture<List<ReconResult>>> futures = internalByCp.entrySet().stream()
                .map(e -> CompletableFuture.supplyAsync(() ->
                        reconcile(e.getValue(), externalByCp.getOrDefault(e.getKey(), List.of()), rule)))
                .toList();
                                                    
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream().flatMap(f -> f.join().stream()).toList());
    }

    private ReconResult matchOne(TradeType internal, TradeType external, ReconciliationRule rule) {
        String ref = internal.tradeRef().value();
        if (external == null) {
            return ReconResult.breakResult(ref, "MISSING_EXTERNAL", "No external trade found for " + ref);
        }
        BigDecimal[] ip = priceQty(internal);
        BigDecimal[] ep = priceQty(external);
        if (rule.matches(ip[0], ip[1], ep[0], ep[1])) {
            return ReconResult.matched(ref);
        }
        return ReconResult.breakResult(ref, "VALUE_MISMATCH",
                "price/qty drift exceeded rule=" + rule.name());
    }

    /** Exhaustive switch over the sealed TradeType hierarchy — compiler enforces all cases handled. */
    private BigDecimal[] priceQty(TradeType t) {
        return switch (t) {
            case EquityTrade e     -> new BigDecimal[]{ e.price(),     e.quantity()     };
            case FXTrade f         -> new BigDecimal[]{ f.fxRate(),    f.notionalCcy1() };
            case BondTrade b       -> new BigDecimal[]{ b.faceValue(), BigDecimal.ONE   };
            case DerivativeTrade d -> new BigDecimal[]{ d.strike(),    d.quantity()     };
        };
    }
}
