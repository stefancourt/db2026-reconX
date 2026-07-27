package com.dbtraining.reconx.observability;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * ============================================================================
 * TICKET-ADV059 — DatabaseHealthIndicator (timed SELECT 1)
 *
 * WHAT:    Custom actuator HealthIndicator that runs a fast `SELECT 1` with
 *          a 2-second timeout and reports latencyMs as a detail.
 * HOW:     Extends AbstractHealthIndicator; Spring picks it up by bean name
 *          ("database") and exposes it under /actuator/health/database. Any
 *          exception thrown out of doHealthCheck is converted to DOWN by the
 *          superclass, with the exception class attached as a detail.
 * WHY:     The default DataSource health indicator works, but a custom one
 *          gives us a controllable timeout AND visible latency for SRE
 *          dashboards.
 * OBSERVE: GET /api/actuator/health/database -> `{"status":"UP",
 *          "details":{"latencyMs": <number>}}`.
 * ============================================================================
 */
@Component("database")
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private final DataSource ds;

    public DatabaseHealthIndicator(DataSource ds) { this.ds = ds; }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        long start = System.nanoTime();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.setQueryTimeout(2);
            s.execute("SELECT 1");
            builder.up().withDetail("latencyMs", (System.nanoTime() - start) / 1_000_000);
        }
    }
}
