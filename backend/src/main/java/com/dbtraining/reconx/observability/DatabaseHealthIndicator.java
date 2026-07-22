package com.dbtraining.reconx.observability;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * ============================================================================
 * TICKET-ADV059 — DatabaseHealthIndicator (timed SELECT 1)
 *
 * WHAT:    Custom actuator HealthIndicator that runs a fast `SELECT 1` with
 *          a 2-second timeout and reports latencyMs as a detail.
 * HOW:     Extends AbstractHealthIndicator; Spring picks it up by bean name
 *          and exposes it under /actuator/health/database.
 * WHY:     The default DataSource health indicator works, but a custom one
 *          gives us a controllable timeout AND visible latency for SRE
 *          dashboards.
 * OBSERVE: GET /api/actuator/health/database -> `{"status":"UP",
 *          "details":{"latencyMs": <number>}}`.
 * ============================================================================
 *
 *  TODO(TICKET-ADV059):
 *    long start = System.nanoTime();
 *    try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
 *        s.setQueryTimeout(2);
 *        s.execute("SELECT 1");
 *        builder.up().withDetail("latencyMs", (System.nanoTime() - start) / 1_000_000);
 *    }
 *
 *  HINT: Throw any exception out of this method — AbstractHealthIndicator
 *        converts it to DOWN with the exception class as a detail.
 * ============================================================================
 */
@Component("database")
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private final DataSource ds;

    public DatabaseHealthIndicator(DataSource ds) { this.ds = ds; }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        // TODO(TICKET-ADV059): run `SELECT 1` with a 2s timeout and record latencyMs.
        builder.up();
    }
}
