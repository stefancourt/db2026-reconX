package com.dbtraining.reconx.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV079 — Verify Liquibase ran on a fresh DB
 *
 * WHAT:    Boots against an empty postgres:16-alpine and asserts that every
 *          changeset in the master changelog was applied and that the seed
 *          data landed.
 * HOW:     Its own @ServiceConnection container (same pattern as ADV078) plus
 *          a JdbcTemplate — raw SQL, so the assertion is about the database,
 *          not about the JPA mapping.
 * WHY:     Safety net for the Day 1 / Day 4 / Day 5 changelog stack. A
 *          changeset XML that was written but never committed, or an include
 *          missing from db.changelog-master.xml, fails here on a clean
 *          container instead of in the Day 10 pipeline.
 * OBSERVE: Green run. `relation "databasechangelog" does not exist` means
 *          Liquibase never ran — check spring.liquibase.enabled and the
 *          changelog path.
 *
 * NOTE ON THE STUDENT GUIDE'S REFERENCE SOLUTION: it asserts >= 13 changesets
 * and >= 10 non-deleted seed trades. Both numbers are wrong for this codebase.
 * The master changelog includes 10 files holding 21 changesets, and 008-seed.xml
 * seeds counterparties, instruments and users — there is no trades seed at all,
 * so the trade assertion here is that the table exists and is queryable through
 * the ADV067 deleted_at column, and the row-count assertions are on the
 * reference data that is genuinely shipped.
 * ============================================================================
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class LiquibaseMigrationsIT {

    /** Expected changesets: 001=1, 002=5, 003=2, 004=1, 005=1, 006=3, 007=1, 008=3, 009=3, 010=1. */
    private static final int EXPECTED_CHANGESETS = 21;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;

    @Test
    void liquibase_applied_all_expected_changesets() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog", Integer.class);

        // >= rather than == so adding a changeset does not break this test; a
        // *missing* one still does, which is the failure this test exists for.
        assertThat(applied).isGreaterThanOrEqualTo(EXPECTED_CHANGESETS);
    }

    @Test
    void seed_data_landed() {
        Integer counterparties = jdbc.queryForObject(
                "SELECT COUNT(*) FROM counterparties", Integer.class);
        assertThat(counterparties).isGreaterThanOrEqualTo(10);   // data/counterparties.csv

        Integer instruments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM instruments", Integer.class);
        assertThat(instruments).isPositive();                    // data/instruments.csv

        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email IN "
                        + "('admin@db.com','trader@db.com','viewer@db.com','recon@db.com')",
                Integer.class);
        assertThat(users).isEqualTo(4);                          // 008-seed-users
    }

    @Test
    void trades_table_carries_the_soft_delete_column() {
        // 010-trades-deleted-at.xml. If the changeset is missing this throws
        // "column deleted_at does not exist" rather than returning a count.
        Integer live = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trades WHERE deleted_at IS NULL", Integer.class);
        assertThat(live).isNotNegative();
    }
}
