# Day 6 — Solved Files & How To Run

Day 6 is the observability day. You turn the app into something an SRE
can actually reason about — cache hits on the hot symbol-lookup path,
and Micrometer metrics wired to `/actuator/prometheus` so Grafana can
scrape them.

**How this folder works**

The real `backend/` tree ships these two files as starter stubs — one
still-open `TODO(TICKET-…)` on `TradeMetrics` and one on
`InstrumentService`. This folder contains **complete drop-in
replacement files** for both:

- `TradeMetrics.java` — Micrometer Counter, DistributionSummary, and polled Gauge registrations plus the two increment/record helpers.
- `InstrumentService.java` — the `findBySymbol` body behind `@Cacheable("instruments")`.

You can **overlay** the whole `backend/` subtree in one shot, or
**open each file** in this folder side-by-side with the starter to
read the diff first.

**In this file:**

1. One-line copy command.
2. Ticket status table.
3. What each file does.
4. Step-by-step run guide, including a curl walkthrough that shows the metrics on `/actuator/prometheus` and the cache stats on `/actuator/caches`.
5. Troubleshooting.

---

## Quick start

```bash
# From the project root:
cp -R day6-solved-files/backend/ backend/
```

---

## Ticket status

Day 6 has 17 tickets (ADV081–097). Most are configuration, dependency
management, or Grafana dashboards that live outside the backend
codebase.

| Ticket | Status | Where |
|---|---|---|
| ADV081 — @Cacheable on `findBySymbol` | ✓ in this folder | `InstrumentService.java` |
| ADV082 — Caffeine cache spec + TTL | ✓ in `application.yml` | `spring.cache.caffeine.spec` |
| ADV083 — `trade_created_total` Counter | ✓ in this folder | `TradeMetrics.java` |
| ADV084 — `@Timed` on `reconcile()` | ✓ Day-3 folder | `ReconciliationEngine.java` |
| ADV085 — `recon_break_count` polled Gauge | ✓ already in starter | `TradeMetrics` constructor |
| ADV086 — `trade_value_total` DistributionSummary | ✓ in this folder | `TradeMetrics.java` |
| ADV087–092 — Actuator exposure, health probes, correlation IDs, JSON logs, log levels | ✓ already in starter | `application.yml`, `logback-spring.xml` |
| ADV093–096 — Docker healthchecks, Grafana dashboards, alert rules | infra — see `monitoring/` folder in project root | — |
| ADV097 — Prometheus scrape config | ✓ already in `monitoring/prometheus.yml` | — |

---

## What each file does

### `TradeMetrics.java` — ADV083 + ADV086

The `Counter` and `DistributionSummary` are constructed once in the
constructor and stored as final fields. The two public methods called
from `TradeService.create()` are now:

- `incrementTradeCreated()` → `tradeCreated.increment();`
- `recordTradeValue(double value)` → `tradeValue.record(value);`

The polled `Gauge` for `recon_break_count` was already wired in the
starter — Micrometer holds a strong reference to `breakRepo` via the
builder, so the gauge lives as long as the registry.

### `InstrumentService.java` — ADV081

The `@Cacheable("instruments")` annotation was already on the method;
this folder fills in the body:

```java
return repo.findBySymbol(symbol)
        .orElseThrow(() -> new InvalidTradeException("Unknown instrument symbol: " + symbol));
```

First call hits the DB; every subsequent call for the same symbol is
served from the Caffeine cache (max size 500, TTL 5 min — see the
`caffeine.spec` in `application.yml`). Verify the hit ratio via
`/actuator/caches/instruments`.

---

## Run the project

### Before you start

1. **Java 21.** `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
2. **You're in the project root.**
3. **You copied the solved files:** `cp -R day6-solved-files/backend/ backend/`.
4. **Days 1–5 are applied** — Day 6 depends on the earlier layers. Overlay them all:
   ```bash
   for d in day1 day2 day3 day4 day5; do cp -R ${d}-solved-files/backend/ backend/; done
   ```

### Step 1 — Compile

```bash
cd backend
./mvnw -q clean compile   # want exit 0
```

### Step 2 — Boot

```bash
./mvnw spring-boot:run
```

Wait for `Started ReconxApplication in ~4 seconds`.

### Step 3 — Prove the metrics + cache work

```bash
# metrics endpoint should list your custom instruments
curl -s http://localhost:8081/api/actuator/prometheus | grep -E "trade_created_total|trade_value_total|recon_break_count"

# cache stats
curl -s http://localhost:8081/api/actuator/caches/instruments
```

You should see `trade_created_total_total 0.0` right after boot; POST
a trade (once you've filled in `AuthController.login` and
`TradeController.create` from Day 5), then re-scrape the endpoint and
watch the counter tick up.

For the reconciliation timer (ADV084, wired in Day 3), grep for
`reconciliation_duration_seconds` in the same output — after you
trigger a recon run, the histogram buckets will show real latency
percentiles.

Hit `Ctrl+C` when done.

---

## Troubleshooting

- **`No qualifying bean of type MeterRegistry`** — Actuator dependency missing. Confirm `spring-boot-starter-actuator` and `micrometer-registry-prometheus` are on the classpath (they are by default in this project).
- **`/actuator/caches` returns 404** — actuator exposure doesn't include `caches`. Add it under `management.endpoints.web.exposure.include` in `application.yml`.
- **Cache never hits** — you either have two `InstrumentService` beans (unlikely), or Spring is proxying the wrong one because you're calling `findBySymbol` from another method inside the same class (self-invocation bypasses AOP). Call it from a different bean.
- **`trade_value_total` histogram has no buckets** — that's the `.publishPercentileHistogram()` call. If you copied `TradeMetrics.java` clean, you're fine.
- **Port 8081 in use** — `lsof -i :8081; kill <PID>`.

That's the SRE surface. Next stop is Day 7.
