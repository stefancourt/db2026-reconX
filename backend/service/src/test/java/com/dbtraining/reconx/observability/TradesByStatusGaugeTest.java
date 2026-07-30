package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.domain.TradeStatus;
import com.dbtraining.reconx.repository.TradeRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TICKET-ADV092 — one tagged gauge series per TradeStatus. */
class TradesByStatusGaugeTest {

    private final TradeRepository repo = mock(TradeRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void registersOneSeriesPerStatus() {
        new TradesByStatusGauge(registry, repo);

        assertThat(registry.find("trades_by_status").gauges())
                .hasSize(TradeStatus.values().length)
                .extracting(g -> g.getId().getTag("status"))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(TradeStatus.values()).map(Enum::name).toList());
    }

    @Test
    void eachGaugeReportsTheCountForItsOwnStatus() {
        when(repo.countByStatus(TradeStatus.PENDING)).thenReturn(7L);
        when(repo.countByStatus(TradeStatus.MATCHED)).thenReturn(3L);

        new TradesByStatusGauge(registry, repo);

        assertThat(gaugeFor(TradeStatus.PENDING).value()).isEqualTo(7.0);
        assertThat(gaugeFor(TradeStatus.MATCHED).value()).isEqualTo(3.0);
        // Unstubbed statuses fall back to Mockito's 0L — the same value an empty
        // table reports, and the reason Grafana hides those slices.
        assertThat(gaugeFor(TradeStatus.DISPUTED).value()).isZero();
    }

    @Test
    void gaugeIsPolledSoLaterCountsAreVisible() {
        when(repo.countByStatus(TradeStatus.MATCHED)).thenReturn(1L);

        new TradesByStatusGauge(registry, repo);
        Gauge matched = gaugeFor(TradeStatus.MATCHED);
        assertThat(matched.value()).isEqualTo(1.0);

        // A polled gauge re-reads its source on every scrape, so a status
        // transition after registration must show up without re-registering.
        when(repo.countByStatus(TradeStatus.MATCHED)).thenReturn(9L);

        assertThat(matched.value()).isEqualTo(9.0);
    }

    private Gauge gaugeFor(TradeStatus status) {
        return registry.get("trades_by_status").tag("status", status.name()).gauge();
    }
}