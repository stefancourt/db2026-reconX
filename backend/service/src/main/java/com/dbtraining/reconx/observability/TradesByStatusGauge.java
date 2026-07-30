package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.domain.TradeStatus;
import com.dbtraining.reconx.repository.TradeRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV092 — trades_by_status Gauge, one series per TradeStatus
 *
 * WHAT:    Publishes {@code trades_by_status{status="..."}} to
 *          /actuator/prometheus — one polled gauge per enum constant.
 * HOW:     One meter name, the {@code status} tag differentiates the series.
 *          Each gauge polls {@code TradeRepository.countByStatus} on scrape.
 * WHY:     The ADV083 {@code trade_created_total} counter only fires at create
 *          time, so it cannot reflect later status transitions. A gauge over
 *          the current tally is the only honest source for a distribution
 *          chart of present state.
 *
 * A status with no trades reports 0 and Grafana hides the slice — expected.
 * ============================================================================
 */
@Component
public class TradesByStatusGauge {

    public TradesByStatusGauge(MeterRegistry registry, TradeRepository repo) {
        for (TradeStatus status : TradeStatus.values()) {
            // The builder captures repo, so the gauge source outlives GC —
            // same strong-reference requirement as TradeMetrics' recon_break_count.
            Gauge.builder("trades_by_status", repo, r -> r.countByStatus(status))
                    .tag("status", status.name())
                    .description("Trades currently in a given status")
                    .register(registry);
        }
    }
}