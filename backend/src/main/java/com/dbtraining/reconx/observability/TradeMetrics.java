package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.repository.ReconBreakRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV083 — trade_created_total Counter
 * TICKET-ADV085 — recon_break_count Gauge (polled — wraps repo.countByStatus)
 * TICKET-ADV086 — trade_value_total DistributionSummary
 *
 * WHAT:    Holds Micrometer instruments published to /actuator/prometheus.
 * HOW:     Counters / Distribution Summaries are constructed once in the
 *          constructor and stored as final fields. Gauges are "polled" —
 *          Micrometer holds a weak reference and calls the lambda on scrape.
 * WHY:     Three different metric shapes matter:
 *            - Counter: monotonic count of events (created trades)
 *            - DistributionSummary: histogram of magnitudes (trade values)
 *            - Gauge: instantaneous value (open recon breaks)
 *
 * The TIMER for reconciliation duration lives as @Timed on
 * ReconciliationEngine.reconcile() (TICKET-ADV084) — not in this class.
 * ============================================================================
 *
 *  TODO(TICKET-ADV083 + ADV086):
 *    public void incrementTradeCreated() { tradeCreated.increment(); }
 *    public void recordTradeValue(double value) { tradeValue.record(value); }
 *
 *  HINT: A polled Gauge MUST hold a strong reference to its source object,
 *        otherwise it disappears on GC. Here breakRepo is captured by the
 *        Gauge.builder so the lifetime is tied to the registry.
 * ============================================================================
 */
@Component
public class TradeMetrics {

    private final Counter tradeCreated;
    private final DistributionSummary tradeValue;

    public TradeMetrics(MeterRegistry registry, ReconBreakRepository breakRepo) {
        this.tradeCreated = Counter.builder("trade_created_total")
                .description("Total trades created")
                .register(registry);

        this.tradeValue = DistributionSummary.builder("trade_value_total")
                .description("Distribution of trade notional values")
                .baseUnit("USD")
                .publishPercentileHistogram()
                .register(registry);

        // TICKET-ADV085 — polled gauge wrapping a repository count.
        Gauge.builder("recon_break_count", breakRepo, r -> r.countByStatus("OPEN"))
                .description("Open recon breaks")
                .register(registry);
    }

    public void incrementTradeCreated() {
        // TODO(TICKET-ADV083): increment the tradeCreated counter.
    }

    public void recordTradeValue(double value) {
        // TODO(TICKET-ADV086): record the value on the tradeValue distribution summary.
    }
}
