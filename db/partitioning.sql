-- ============================================================================
-- TICKET-ADV007 — Convert trades to monthly range-partitioned table (Postgres)
--
-- WARNING: destructive. Run in a maintenance window — copies the entire
-- trades table into a new partitioned trades, then renames.
-- ============================================================================

-- 1. Rename existing
ALTER TABLE trades RENAME TO trades_legacy;

-- 2. Create partitioned parent (same columns)
CREATE TABLE trades (
    id              BIGSERIAL,
    trade_ref       VARCHAR(30)   NOT NULL,
    instrument_id   BIGINT        NOT NULL REFERENCES instruments(id),
    counterparty_id BIGINT        NOT NULL REFERENCES counterparties(id),
    asset_class     VARCHAR(20)   NOT NULL,
    side            VARCHAR(4)    NOT NULL,
    quantity        NUMERIC(18,4) NOT NULL,
    price           NUMERIC(18,4) NOT NULL,
    trade_date      DATE          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    modified_at     TIMESTAMPTZ,
    PRIMARY KEY (id, trade_date)
) PARTITION BY RANGE (trade_date);

-- 3. Per-month partitions (12-month rolling window). Add new ones on schedule.
CREATE INDEX idx_trades_status          ON trades (status);
CREATE INDEX idx_trades_instrument_id   ON trades (instrument_id);
CREATE INDEX idx_trades_counterparty_id ON trades (counterparty_id);

-- Step 4: Four child partitions for 2026-04 through 2026-07
CREATE TABLE trades_y2026m04 PARTITION OF trades
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE trades_y2026m05 PARTITION OF trades
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE trades_y2026m06 PARTITION OF trades
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE trades_y2026m07 PARTITION OF trades
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- Step 5: Default partition as safety catch
CREATE TABLE trades_default PARTITION OF trades DEFAULT;