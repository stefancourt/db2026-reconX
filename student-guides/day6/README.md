# Day 6 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day6/README.md](../../TrainersGuide/day6/README.md)
> **Module:** Spring Boot Modules 5 & 6 — Performance + Observability

## What you'll build today

Today you graduate ReconX from "works correctly" to "works fast, and we can prove it." You will add an in-process Caffeine cache with per-cache TTLs to make hot reads sub-millisecond, instrument the application with four custom Micrometer meters (Counter, Timer, Gauge, DistributionSummary) that describe real business events rather than HTTP plumbing, and wire those meters end-to-end through Actuator and Prometheus into a Grafana dashboard with six panels and two firing-capable alert rules. You will then package a cross-cutting concern as a reusable Spring Boot starter, expose a runtime-tunable knob through a JMX MBean, and finish with a small load test that you can watch live on the dashboard you just built. By the end of the day the question "is this thing healthy?" should have an answer you can point at on a screen.

## Day at a glance

1. Standup and Day-5 holdover unblock
2. Workshop 6A: Caching with Caffeine (TICKET-ADV081 – TICKET-ADV082)
3. Workshop 6B: Custom Micrometer metrics (TICKET-ADV083 – TICKET-ADV086)
4. Workshop 6C: Grafana panels and alerts, the Observability Deep Dive (TICKET-ADV087 – TICKET-ADV094)
5. Workshop 6D: Custom Spring Boot Starter, JMX, performance test (TICKET-ADV095 – TICKET-ADV097)
6. End-of-day debrief and Day 7 preview

## Exercises

Work the exercises in order. Each one builds on the previous. The hints are layered: open Hint 1 first and try the idea before peeking at Hint 2; open Hint 3 only when you have a concrete attempt and want to confirm its shape. Hints never contain the final code — the goal is for you to write it.

Before you start, confirm Prometheus is reachable at `http://localhost:9090`, Grafana at `http://localhost:3000` (admin/admin), and your Spring Boot app's `/actuator/prometheus` endpoint returns a non-empty response. If any of those are off, fix that first — every exercise downstream depends on the pipeline being live.

### Workshop 6A — Caching with Caffeine

This block adds an in-process cache to one hot read path and gives the cache realistic, per-cache eviction policies. The pattern you learn here is what you would also apply to counterparty lookup, security master joins, and any other read-mostly reference data.

### TICKET-ADV081 — `@Cacheable` on `InstrumentService.findBySymbol()`

**Goal:** Wrap the symbol lookup so a second call for the same symbol hits memory, not the database.

**What**
- `@Cacheable(value = "instruments", key = "#symbol")` on `InstrumentService.findBySymbol` plus a `CacheConfig` class carrying `@EnableCaching`.

**Why**
- Symbol lookup runs on every trade ingest and every break view, so cutting it from a DB round-trip to a memory hit is the foundation the ADV082 TTL policies and the ADV097 load test will measure against.

**Observe**
- After two consecutive `GET /api/v1/instruments/SAP.DE` calls the `DB hit for SAP.DE` log line appears exactly once, and `/actuator/caches` lists an `instruments` entry.

**Done when:**
- A log line inside `findBySymbol` prints on the first call but not the second call for the same symbol.
- `/actuator/caches` lists an `instruments` cache.
- Calling the endpoint twice in succession shows the second response visibly faster (sub-millisecond after the first hit).

<details>
<summary>Hint 1 — gentle direction</summary>

Spring's caching is annotation-driven. There is one annotation you place on the method whose result you want to cache, and one annotation you place on a configuration class to switch the whole mechanism on. Without the second one, the first one is silently parsed and does nothing. Think about what has to be true for an annotation to actually do work in a Spring application — it has to be picked up by a proxy, and the proxy has to be built around the bean.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at `org.springframework.cache.annotation.Cacheable` and `org.springframework.cache.annotation.EnableCaching`. The `@Cacheable` annotation takes a `value` attribute (the cache name, which you will reuse in TICKET-ADV082 as `"instruments"`) and a `key` attribute (an SpEL expression). For the configuration switch, create a `@Configuration` class — `CacheConfig` is a sensible name — and add the enabler annotation at class level. Remember the visibility rule: Spring AOP proxies do not see private methods, and a same-class self-call (`this.findBySymbol(...)`) bypasses the proxy entirely. Verify your method is `public` and that callers reach it through the injected service bean.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`InstrumentService.findBySymbol(String symbol)` is annotated with the caching annotation. The cache `value` is the literal string `"instruments"`. The `key` is the SpEL `"#symbol"` (the leading `#` references the parameter name; with debug info it can also be `#root.args[0]`, but the named form is the documented best practice). A new `CacheConfig` class annotated `@Configuration` carries the enabler annotation at class level — no bean methods required at this stage. Add a `log.info("DB hit for {}", symbol)` line at the top of the method body to make the proof obvious during testing. The endpoint is called twice; you expect exactly one log line.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/config/CacheConfig.java` annotated `@Configuration` and `@EnableCaching`.
2. Open `InstrumentService.findBySymbol(String symbol)` and confirm the method is `public`.
3. Add `@Cacheable(value = "instruments", key = "#symbol")` directly above the method.
4. Add a `log.info("DB hit for {}", symbol)` line at the top of the method body to prove cache behaviour.
5. Restart the app, call `GET /api/v1/instruments/SAP.DE` twice in a row.
6. Confirm exactly one `DB hit` log line, and that `/actuator/caches` lists `instruments`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/InstrumentService.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.exception.InvalidTradeException;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.entity.Instrument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * @Cacheable on findBySymbol (cache name "instruments").
 * TTL configured in application.yml (caffeine spec).
 *
 * Symbol lookup is hot — most requests touch the cache, not the DB.
 */
@Service
public class InstrumentService {

    private final InstrumentRepository repo;

    public InstrumentService(InstrumentRepository repo) { this.repo = repo; }

    @Cacheable("instruments")
    public Instrument findBySymbol(String symbol) {
        return repo.findBySymbol(symbol)
                .orElseThrow(() -> new InvalidTradeException("Unknown instrument symbol: " + symbol));
    }
}
```

`@EnableCaching` is wired on the main application class (no separate `CacheConfig` in the trainer copy — the Caffeine spec lives in `application.yml`, see TICKET-ADV082).

</details>

**▶ Run the project — verify TICKET-ADV081 end-to-end**

Confirm the `@Cacheable` annotation actually proxies the instrument lookup so the second call skips the DB.

```bash
./mvnw -pl backend spring-boot:run
# in another terminal:
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/instruments/1
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/instruments/1
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/caches
```

**Observe:**

- First request logs a `DB hit for ...` line in the Spring console; the second request does not.
- `/actuator/caches` lists an `instruments` cache.
- If both calls log `DB hit`, `@EnableCaching` is missing or the method is being self-called inside the same class (proxy bypassed).

---

### TICKET-ADV082 — Cache eviction: TTL 5 min instruments, 1 min counterparties

**Goal:** Configure two separately-named Caffeine caches with different time-to-live policies and turn on the stats hooks that Micrometer needs.

**What**
- A `CaffeineCacheManager` bean defining `instruments` (5 min TTL) and `counterparties` (1 min TTL) with `recordStats()` enabled so Micrometer can scrape hit/miss counts.

**Why**
- Different reference data ages at different rates; mixing them in one cache means either stale counterparties or excess DB load on symbols, and the stats hook is what feeds the cache panels you will need for the ADV087/ADV097 dashboards.

**Observe**
- `/actuator/prometheus` exposes `cache_gets_total{cache="instruments",result="hit"}` after a warm read, and a counterparty cached at T=0 has expired by T=1m30s.

**Done when:**
- `/actuator/caches` lists both `instruments` and `counterparties`.
- A symbol cached at T=0 has expired by T=5m30s; a counterparty cached at T=0 has expired by T=1m30s.
- `/actuator/prometheus` exposes a `cache_gets_total` series with labels `cache="instruments"` and `result="hit"` after a warm read.

<details>
<summary>Hint 1 — gentle direction</summary>

The `spring.cache.caffeine.spec` YAML property accepts one global Caffeine spec string. That is fine when all your caches want the same policy — but yours want two different TTLs. There is a more programmatic route: declare a `CacheManager` bean yourself and register one configured Caffeine cache per name. Think about which API gives you per-cache control rather than blanket control, and what type the cache manager bean should be.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Two dependencies must be on the classpath: `com.github.ben-manes.caffeine:caffeine` and `org.springframework.boot:spring-boot-starter-cache`. Without the first, Spring quietly falls back to a `ConcurrentMapCacheManager` that ignores TTL config and you spend an hour debugging a working-but-not-evicting cache. The relevant classes are `com.github.benmanes.caffeine.cache.Caffeine` (the builder), `org.springframework.cache.caffeine.CaffeineCache` (the Spring wrapper), and `org.springframework.cache.support.SimpleCacheManager` (the holder). Use `Caffeine.newBuilder()`, chain `.expireAfterWrite(duration, unit)`, `.maximumSize(...)`, and `.recordStats()`, then `.build()`. Wrap each Caffeine instance in a `CaffeineCache(name, caffeine)` and feed a list of them to the simple manager.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

In `CacheConfig` (already carrying the enabler from TICKET-ADV081) add an `@Bean CacheManager cacheManager()` method. Build two `Caffeine` instances. The instruments cache uses `expireAfterWrite(5, TimeUnit.MINUTES)`, a `maximumSize` of around 500, and `recordStats()`. The counterparties cache uses `expireAfterWrite(1, TimeUnit.MINUTES)`, a `maximumSize` of around 200, and `recordStats()`. Wrap each in a `CaffeineCache` with the matching name string. Create a `SimpleCacheManager`, call `setCaches(List.of(instruments, counterparties))`, and return it. The `recordStats()` call is what lets `cache_gets_total` reach Prometheus; without it the cache works but the dashboard panel will be blank in Workshop 6C.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add the `caffeine` and `spring-boot-starter-cache` dependencies to `backend/pom.xml`.
2. Set `spring.cache.type: caffeine` and `spring.cache.cache-names: instruments,counterparties` in `application.yml`.
3. In `CacheConfig`, declare an `@Bean CacheManager cacheManager()` method.
4. Build two `Caffeine` instances with different `expireAfterWrite`, `maximumSize`, and `recordStats()`.
5. Wrap each in a `CaffeineCache("instruments", ...)` / `CaffeineCache("counterparties", ...)` and feed both to a `SimpleCacheManager`.
6. Boot the app, hit `/actuator/caches` and confirm both names are listed.
7. After a warm read, confirm `/actuator/prometheus` exposes `cache_gets_total{cache="instruments",result="hit"}`.

**Reference solution** (`backend/pom.xml` — caching dependencies):

```xml
<!-- Caching (TICKET-ADV081-TICKET-ADV082) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

`backend/src/main/resources/application.yml` (TICKET-ADV081–TICKET-ADV082 — Caffeine cache config):

```yaml
spring:
  # TICKET-ADV081-TICKET-ADV082 — Caffeine cache config; per-cache spec overrides the global one.
  cache:
    cache-names: instruments,counterparties
    caffeine:
      spec: maximumSize=500,expireAfterWrite=5m
```

Trainer copy uses the single `spring.cache.caffeine.spec` global string for runnability; `@EnableCaching` sits on `ReconxApplication`. To give the two caches different TTLs (5 min instruments, 1 min counterparties), upgrade to a programmatic `CacheManager` bean per the trainer-guide reference:

```java
package com.dbtraining.reconx.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache instruments = new CaffeineCache("instruments",
            Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .recordStats()
                    .build());

        CaffeineCache counterparties = new CaffeineCache("counterparties",
            Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(1, TimeUnit.MINUTES)
                    .recordStats()
                    .build());

        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(instruments, counterparties));
        return mgr;
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV082 end-to-end**

Confirm both named caches load with the configured TTL/maxSize specs and that Caffeine stats are wired into Prometheus.

```bash
./mvnw -pl backend spring-boot:run
curl -s http://localhost:8080/actuator/caches | jq
curl -s http://localhost:8080/actuator/caches/instruments | jq
curl -s http://localhost:8080/actuator/prometheus | grep '^cache_'
```

**Observe:**

- `/actuator/caches` lists both `instruments` and `counterparties`.
- The configured spec on each cache matches the `application.yml` (or `CacheConfig`) values for `expireAfterWrite` and `maximumSize`.
- After a warm read, `cache_gets_total{cache="instruments",result="hit"}` appears in the Prometheus scrape; if the series is missing, `recordStats()` was not chained on the `Caffeine` builder.

---

### Workshop 6B — Custom Micrometer metrics

This block is where you stop relying on the metrics Actuator gives you for free and start *designing* metrics for your domain. Before you touch a keyboard for any of these four exercises, write down the question the metric answers in one sentence. If the question is "how many," reach for a Counter. If it is "how long," reach for a Timer. If it is "what is the value right now," reach for a Gauge. If it is "what is the distribution of a non-time value," reach for a DistributionSummary. The wrong meter type makes the right PromQL impossible — slow down at the start.

### TICKET-ADV083 — Counter: `trade_created_total`

**Goal:** Emit a monotonically increasing counter every time a trade is successfully created.

**What**
- A `Counter.builder("trade_created_total").register(meterRegistry)` bean wired into `TradeService.createTrade`, incremented once per successful create.

**Why**
- This is the source-of-truth counter the ADV089 Grafana rate panel charts and the ADV097 load test verifies — without it the trade-throughput SLO has no signal.

**Observe**
- After three successful `POST /api/v1/trades` calls, `curl /actuator/prometheus | grep trade_created_total` shows the value increased by exactly three with a `# HELP` line attached.

**Done when:**
- `/actuator/prometheus` lists a metric named `trade_created_total` with a `# HELP` line describing what it counts.
- Creating three trades via the API and re-scraping shows the value increased by exactly three.
- The Counter is constructed once at bean creation, not inside the service method.

<details>
<summary>Hint 1 — gentle direction</summary>

A Counter, like a Timer or Gauge, is a single object that you register with the `MeterRegistry` once. Then you keep a reference and call its increment method on each event. The mistake to avoid is constructing the Counter inside a method body — every call would try to register a new meter with the same name, which Micrometer rejects after the first time. Where in a Spring-managed bean do you do things exactly once?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Create a small dedicated component class — `TradeMetrics` is a good name — that takes a `MeterRegistry` in its constructor. Inside the constructor, build a Counter using the fluent builder `io.micrometer.core.instrument.Counter.builder(name)`. Chain a `.description(...)` so `/actuator/prometheus` reads well, optionally a `.tag(key, value)`, then `.register(registry)`. Store the result in a `private final Counter` field. Expose an `incrementCreated()` method that calls `tradesCreated.increment()`. Inject `TradeMetrics` into `TradeService` and call the method after a successful save.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Metric name: `trade_created_total` (Prometheus convention: snake_case, `_total` suffix on counters). The Counter is a `private final` field initialised in the `@Component`'s constructor. The increment site is inside `TradeService.create(...)` *after* `repo.save(...)` returns — not before, otherwise a failed save still counts. Verify on `/actuator/prometheus` by `grep`ping for `trade_created_total` and confirming the value rises by exactly one per successful POST. The description string is what an SRE will read at 2 a.m., so make it specific: "Total number of trades created via the API."

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/observability/TradeMetrics.java` annotated `@Component`.
2. Inject `MeterRegistry` via constructor; build the Counter once with `.builder(...).description(...).register(registry)`.
3. Hold the Counter in a `private final` field; expose `incrementCreated()`.
4. Inject `TradeMetrics` into `TradeService` and call `incrementCreated()` after `repo.save(...)`.
5. POST three trades and `curl /actuator/prometheus | grep trade_created_total` — value must rise by exactly three.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/observability/TradeMetrics.java` — Counter portion; the full file also holds the Gauge from TICKET-ADV085 and the DistributionSummary from TICKET-ADV086):

```java
package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.repository.ReconBreakRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TradeMetrics {

    private final Counter tradeCreated;

    public TradeMetrics(MeterRegistry registry, ReconBreakRepository breakRepo) {
        this.tradeCreated = Counter.builder("trade_created_total")
                .description("Total trades created")
                .register(registry);
    }

    public void incrementTradeCreated() { tradeCreated.increment(); }
}
```

Call site inside `backend/src/main/java/com/dbtraining/reconx/service/TradeService.java` (`create(...)`):

```java
Trade saved = tradeRepo.save(t);
metrics.incrementTradeCreated();
metrics.recordTradeValue(saved.getQuantity().multiply(saved.getPrice()).doubleValue());
```

</details>

**▶ Run the project — verify TICKET-ADV083 end-to-end**

Confirm the custom Counter is registered once at bean creation and increments exactly once per successful trade save.

```bash
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/v1/trades \
  -d '{"tradeRef":"T-ADV083","instrumentSymbol":"SAP.DE","counterpartyId":1,"quantity":100,"price":245.5,"tradeDate":"2026-06-02"}'
curl -s http://localhost:8080/actuator/prometheus | grep trade_created
```

**Observe:**

- `trade_created_total` appears with a `# HELP` line describing what it counts.
- The counter rises by exactly 1 per successful POST.
- If the metric is missing entirely, the `TradeMetrics` component was not picked up (check `@Component`); if it appears but never moves, the `incrementTradeCreated()` call is on a path that did not commit (e.g., before `repo.save`).

---

### TICKET-ADV084 — Timer: `reconciliation_duration_seconds`

**Goal:** Measure how long the reconciliation engine takes per batch and publish the distribution so server-side percentile queries work in PromQL.

**What**
- A `Timer` named `reconciliation_duration_seconds` registered with `publishPercentileHistogram(true)`, wrapping the recon engine call via `timer.record(() -> engine.run(batch))`.

**Why**
- Publishing buckets server-side is what lets ADV090's heatmap and ADV094's P95 alert query `histogram_quantile(0.95, ...)` cheaply in PromQL instead of streaming raw samples.

**Observe**
- `/actuator/prometheus` lists `reconciliation_duration_seconds_count`, `_sum`, and `_bucket{le="..."}` series, and a P95 PromQL query returns a non-zero value after a few recon runs.

**Done when:**
- `/actuator/prometheus` lists `reconciliation_duration_seconds_count`, `_sum`, and `_bucket` series.
- A PromQL query using `histogram_quantile(0.95, ...)` against `_bucket` returns a non-zero value once you have triggered a few reconciliations.
- The Timer wraps the engine call without losing the return value the caller needs.

<details>
<summary>Hint 1 — gentle direction</summary>

A Timer has two recording styles. The first is start/stop with a `Timer.Sample` — flexible but easy to leak if you forget the stop. The second is a single call that takes a lambda, runs it, and records the elapsed time automatically. For a method that returns a value (like `ReconResult`), there is a variant that takes a supplier and returns the value. Pick the lambda style — it is impossible to forget the stop. Also think about which optional builder method makes server-side percentiles (the `histogram_quantile` kind, computed in Prometheus) possible at all.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `io.micrometer.core.instrument.Timer.builder(name)`. Chain `.description(...)`, `.publishPercentileHistogram()` (the one that emits `_bucket` series for Prometheus to interpolate), and optionally `.publishPercentiles(0.5, 0.95, 0.99)` for client-side pre-computed percentiles (a separate `_quantile` series — useful for non-Prometheus backends). Register with the meter registry and hold the Timer as a field. The recording call shape is `timer.record(Supplier<T>)` when the wrapped call returns a value — it returns the same `T`. Wrap the recon engine call site, not the controller — the controller's HTTP timing is already captured by `http_server_requests_seconds`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Metric name: `reconciliation_duration_seconds` (Prometheus convention: time in base unit seconds, the unit suffix in the name). The Timer is built in the constructor of a `ReconMetrics` component with `.publishPercentileHistogram()` *required* — without it you get `_count` and `_sum` only and `histogram_quantile()` returns NaN. The call site is whichever service method invokes the engine; the body becomes `return reconMetrics.reconciliationTimer().record(() -> engine.reconcile(batchId))`. Confirm by triggering the recon a handful of times and querying `histogram_quantile(0.95, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))` in Prometheus's expression browser.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/observability/ReconMetrics.java` annotated `@Component`.
2. Build the Timer once in the constructor with `.publishPercentileHistogram()` and `.publishPercentiles(0.5, 0.95, 0.99)`.
3. Expose a `reconciliationTimer()` accessor returning the held `Timer` field.
4. In the service that invokes the engine, wrap the call: `reconMetrics.reconciliationTimer().record(() -> engine.reconcile(batchId))`.
5. Trigger several reconciliations, then query `histogram_quantile(0.95, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))` in Prometheus.

**Reference solution** — trainer copy uses Micrometer's declarative `@Timed` directly on the engine method rather than a programmatic Timer field. The Actuator histogram is switched on via `management.metrics.distribution.percentiles-histogram.reconciliation.duration: true` in `application.yml`.

`backend/src/main/java/com/dbtraining/reconx/service/ReconciliationEngine.java` (relevant signature):

```java
@Service
public class ReconciliationEngine {

    @Timed(value = "reconciliation.duration", description = "Wall time of reconcile()",
           percentiles = {0.5, 0.95, 0.99}, histogram = true)
    public List<ReconResult> reconcile(List<TradeType> internal,
                                       List<TradeType> external,
                                       ReconciliationRule rule) {
        // ... matching logic (parallel stream over internal trades)
    }
}
```

`backend/src/main/resources/application.yml` (histogram exposure):

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
        reconciliation.duration: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
```

`@Timed` requires `TimedAspect` to be wired (auto-configured when `spring-boot-starter-aop` is on the classpath transitively via the actuator starter). The metric appears in Prometheus as `reconciliation_duration_seconds_{count,sum,bucket}`. The trainer-guide programmatic-Timer variant (a dedicated `ReconMetrics` component holding a `Timer.builder(...).publishPercentileHistogram()` field) is an equally valid solution and is shown in the trainer guide if you prefer the imperative form.

</details>

**▶ Run the project — verify TICKET-ADV084 end-to-end**

Confirm the reconcile method is timed with a histogram so server-side `histogram_quantile` queries succeed.

```bash
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in 1 2 3 4 5; do
  curl -s -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/v1/recon/run
done
curl -s http://localhost:8080/actuator/prometheus | grep reconciliation
```

**Observe:**

- `reconciliation_duration_seconds_count`, `_sum`, and many `_bucket{le="..."}` series are present.
- Querying `histogram_quantile(0.95, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))` in Prometheus returns a non-zero value.
- If you see only `_count` and `_sum` but no `_bucket` series, the `@Timed(histogram = true)` flag (or `.publishPercentileHistogram()` on the programmatic timer) is missing.

---

### TICKET-ADV085 — Gauge: `recon_break_count`

**Goal:** Publish a current-value sample of how many reconciliation breaks are currently OPEN.

**What**
- A `Gauge.builder("recon_break_count", breakRepository, r -> r.countByStatus(OPEN))` registered at startup so Micrometer pulls the live count on every scrape.

**Why**
- Current-state signals like open-break count cannot be modelled as counters — this gauge is what the ADV091 stat tile reads and the ADV093 alert rule fires against.

**Observe**
- Closing one break via `PATCH /api/v1/breaks/{id}` and re-scraping `/actuator/prometheus` shows `recon_break_count` drop by one with no application code calling `.set(...)`.

**Done when:**
- `/actuator/prometheus` shows `recon_break_count` updating to the live DB count.
- Closing a break via the API and re-scraping shows the value drop.
- The Gauge does not require any code to call `.set(...)` — it pulls from a supplier.

<details>
<summary>Hint 1 — gentle direction</summary>

A Gauge is *pull-based*. You do not push numbers into it; you hand Micrometer a way to read the current number whenever it asks. The "way to read" is typically a method reference on an object — a function from that object to a `double`. Micrometer holds a weak reference to the object, so the object you hand it must live somewhere that keeps it alive (which a Spring-managed bean does naturally). Think about which object in your system already knows how to count open breaks.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`io.micrometer.core.instrument.Gauge.builder(name, stateObject, toDoubleFunction)` is the API. The `stateObject` is the repository (or any bean) that owns the count; the `toDoubleFunction` is a method reference like `MyRepo::countOpenBreaks`. Chain `.description(...)` and `.register(registry)`. The repository needs a query method that returns a long — write a `@Query` with `SELECT COUNT(b) FROM ReconBreak b WHERE b.status = 'OPEN'` on `ReconBreakRepository`. Hold a strong reference to the repository in your component (a constructor-injected `private final` field is sufficient) so the weak reference inside Micrometer's gauge does not let it be collected.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Metric name: `recon_break_count`. Place the Gauge registration in a small `@Component` (call it `BreakCountGauge`) whose constructor receives the `MeterRegistry` and `ReconBreakRepository`. Inside the constructor, call `Gauge.builder("recon_break_count", repo, ReconBreakRepository::countOpenBreaks).description(...).register(registry)`. Add the `countOpenBreaks` method to `ReconBreakRepository` with the JPQL above. Note: every Prometheus scrape (default 15 s) will execute the query — if that ever becomes a problem, cache the result for a few seconds and have the supplier read the cached value. For now, the direct read is fine.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a `countOpenBreaks()` method to `ReconBreakRepository` with `@Query("SELECT COUNT(b) FROM ReconBreak b WHERE b.status = 'OPEN'")`.
2. Create `backend/src/main/java/com/dbtraining/reconx/observability/BreakCountGauge.java` annotated `@Component`.
3. In the constructor, take `MeterRegistry` and `ReconBreakRepository` (strong reference held by Spring).
4. Register the Gauge with `Gauge.builder("recon_break_count", repo, ReconBreakRepository::countOpenBreaks)`.
5. Restart, hit `/actuator/prometheus` — `recon_break_count` should reflect the live DB count.
6. Close a break via the API; after the next scrape the gauge value drops.

**Reference solution** — trainer copy registers the Gauge inside the same `TradeMetrics` constructor (alongside the Counter from TICKET-ADV083), pointed at the existing `countByStatus` repository method.

`backend/src/main/java/com/dbtraining/reconx/observability/TradeMetrics.java` (Gauge portion of the constructor):

```java
// polled gauge wrapping a repository count.
Gauge.builder("recon_break_count", breakRepo, r -> r.countByStatus("OPEN"))
        .description("Open recon breaks")
        .register(registry);
```

`backend/src/main/java/com/dbtraining/reconx/repository/ReconBreakRepository.java`:

```java
package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.ReconBreak;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconBreakRepository extends JpaRepository<ReconBreak, Long> {
    /** exported as recon_break_count gauge. */
    long countByStatus(String status);
}
```

Spring Data derives the JPQL from the method name (`countByStatus`); the trainer-guide reference also shows an equivalent `@Query("SELECT COUNT(b) FROM ReconBreak b WHERE b.status = 'OPEN'")` form. Either spelling is fine. Splitting the gauge into a dedicated `BreakCountGauge` component (as the trainer guide suggests) is also valid and is the recommended structure once `TradeMetrics` grows beyond ~5 meters.

</details>

**▶ Run the project — verify TICKET-ADV085 end-to-end**

Confirm the polled Gauge reflects the live DB count of OPEN breaks.

```bash
./mvnw -pl backend spring-boot:run
curl -s http://localhost:8080/actuator/prometheus | grep recon_break_count
# in psql or H2 console:
#   SELECT COUNT(*) FROM recon_breaks WHERE status='OPEN';
```

**Observe:**

- `recon_break_count` value in Prometheus matches `SELECT COUNT(*) FROM recon_breaks WHERE status='OPEN'`.
- Closing a break via `PUT /api/v1/breaks/{id}/resolve` and re-scraping within ~15s drops the value.
- If the value is always `0` (despite breaks existing), the gauge's `ToDoubleFunction` is likely referencing a stale reference or the wrong status string ("Open" vs "OPEN").

---

### TICKET-ADV086 — DistributionSummary: `trade_value_total`

**Goal:** Record the distribution of trade notional values (in USD) so you can answer questions like "what's the P95 trade size?"

**What**
- A `DistributionSummary.builder("trade_value_total").baseUnit("USD").publishPercentileHistogram(true)` recorded once per successful trade alongside the ADV083 counter.

**Why**
- Counting trades alone hides whether the day's flow is many small tickets or a few jumbos — the distribution is what compliance and risk dashboards (and any future P95-trade-size panel) need.

**Observe**
- `/actuator/prometheus` lists `trade_value_total_count`, `_sum`, and `_bucket` series, and the HELP line names USD as the base unit.

**Done when:**
- `/actuator/prometheus` lists `trade_value_total_count`, `_sum`, and `_bucket` series.
- The metric has a `base_unit` label or HELP line indicating USD.
- The Summary is recorded once per successful trade alongside the TICKET-ADV083 Counter increment.

<details>
<summary>Hint 1 — gentle direction</summary>

A Timer measures *how long*; a DistributionSummary measures *how big*. The Prometheus output shape is similar — both produce `_count`, `_sum`, and (when histogram is enabled) `_bucket` series. The difference is semantic: keep the meter type aligned with the question the metric answers. Think about what unit the value carries, and whether you want server-side percentile interpolation later.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`io.micrometer.core.instrument.DistributionSummary.builder(name)`. Chain `.description(...)`, `.baseUnit("usd")` (the unit string surfaces in `/actuator/prometheus` and ends ambiguity in Workshop 6C), `.publishPercentileHistogram()` for `_bucket` series, then `.register(registry)`. Hold as a `private final` field on the same `TradeMetrics` component you built in TICKET-ADV083. The record method is `tradeValue.record(doubleValue)`. The trade carries a `BigDecimal notional()` — convert via `.doubleValue()` at the record site; precision loss is acceptable for a metric (it would not be for accounting).

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`TradeMetrics` now has two fields: the Counter from TICKET-ADV083 and a new `DistributionSummary tradeValue`. Constructor builds both. A single `recordTrade(Trade t)` method increments the counter and records `t.notional().doubleValue()` on the summary. `TradeService.create(...)` calls `recordTrade` once after a successful save (replacing the previous `incrementCreated` call). Confirm both `trade_created_total` (count of events) and `trade_value_total_sum` (sum of USD notional) move together as you POST trades. PromQL preview: `sum(trade_value_total_sum) / sum(trade_value_total_count)` is the running average trade size.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `TradeMetrics` and add a `private final DistributionSummary tradeValue` field next to the Counter.
2. Build the Summary in the constructor with `.baseUnit("usd")` and `.publishPercentileHistogram()`.
3. Replace `incrementCreated()` with a single `recordTrade(Trade t)` that bumps the counter *and* records `t.notional().doubleValue()`.
4. Update `TradeService.create(...)` to call `recordTrade(saved)` instead of the older method.
5. POST a handful of trades; confirm `/actuator/prometheus` shows `trade_value_total_count`, `_sum`, and `_bucket` series rising.

**Reference solution** — full `backend/src/main/java/com/dbtraining/reconx/observability/TradeMetrics.java` (Counter + DistributionSummary + Gauge in one place):

```java
package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.repository.ReconBreakRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * trade_created_total Counter
 * recon_break_count Gauge (polled — wraps repo.countByStatus)
 * trade_value_total DistributionSummary
 *
 * The TIMER for reconciliation duration lives as @Timed on
 * ReconciliationEngine.reconcile() (TICKET-ADV084).
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

        // polled gauge wrapping a repository count.
        Gauge.builder("recon_break_count", breakRepo, r -> r.countByStatus("OPEN"))
                .description("Open recon breaks")
                .register(registry);
    }

    public void incrementTradeCreated() { tradeCreated.increment(); }
    public void recordTradeValue(double value) { tradeValue.record(value); }
}
```

Call site inside `backend/src/main/java/com/dbtraining/reconx/service/TradeService.java` (`create(...)`):

```java
Trade saved = tradeRepo.save(t);
metrics.incrementTradeCreated();
metrics.recordTradeValue(saved.getQuantity().multiply(saved.getPrice()).doubleValue());
```

Notice the trainer source records `quantity * price` (computed at the call site) rather than a precomputed `notional()` getter on the entity. The base unit is `USD` (uppercase) — surfaces in `/actuator/prometheus` as a HELP-line / unit tag.

</details>

**▶ Run the project — verify TICKET-ADV086 end-to-end**

Confirm all three TradeMetrics members (Counter + DistributionSummary + Gauge) appear together in one Prometheus scrape.

```bash
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in 1 2 3; do
  curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -X POST http://localhost:8080/api/v1/trades \
    -d "{\"tradeRef\":\"T-ADV086-$i\",\"instrumentSymbol\":\"SAP.DE\",\"counterpartyId\":1,\"quantity\":100,\"price\":245.5,\"tradeDate\":\"2026-06-02\"}"
done
curl -s http://localhost:8080/actuator/prometheus | grep -E 'trade_created_total|trade_value_total|recon_break_count'
```

**Observe:**

- All three series — `trade_created_total`, `trade_value_total_{count,sum,bucket}`, and `recon_break_count` — appear together.
- `trade_value_total_sum` grows by `quantity * price` per POST; `_count` grows by 1.
- If `_bucket` series for `trade_value_total` are missing, `publishPercentileHistogram()` was not chained on the `DistributionSummary` builder.

---

### Workshop 6C — Grafana panels and alerts (Observability Deep Dive)

This is the headline block of the day. You will spend two hours here. Before any panel, sketch the pipeline on paper: Spring Boot exposes `/actuator/prometheus`; Prometheus scrapes it every 15 seconds and stores time series; Grafana queries Prometheus over PromQL and renders panels; Prometheus also evaluates alert rules and (in a real environment) ships firing alerts to Alertmanager which routes to Slack/email/webhook. If any panel later shows "No data," walk this pipeline backwards from the panel to the metric on `/actuator/prometheus` — the break is always at one of the four hops.

Open `http://localhost:9090/graph` and run a quick sanity check: query `up{job="recon-service"}`. You should see a value of `1`. If it is missing, your Prometheus job target is wrong or the app is not running. Fix that before continuing.

### TICKET-ADV087 — Grafana panel: API request rate by endpoint

**Goal:** A time-series panel showing requests-per-second broken out by URI.

**What**
- A Grafana time-series panel titled "API request rate by endpoint" running `sum by (uri) (rate(http_server_requests_seconds_count[1m]))` with the Y-axis unit set to req/s.

**Why**
- This is the first panel an SRE opens during an incident and the one ADV097's load test will visibly spike — getting the `by (uri)` grouping right now avoids the high-cardinality tangle that would otherwise hide the signal.

**Observe**
- After hitting `/api/v1/trades` and `/api/v1/breaks` a few times, the panel shows one labelled line per URI and no method/status/instance noise.

**Done when:**
- Panel renders a non-empty time series after you exercise a few endpoints.
- Each line is labelled with its URI, not a tangle of method/status/instance combinations.
- The Y-axis unit reads as requests-per-second.

<details>
<summary>Hint 1 — gentle direction</summary>

The Micrometer-emitted metric you want is the HTTP server counter (it ends in `_count` in Prometheus). To go from a monotonically increasing counter to "requests per second," you need a PromQL function that derives a per-second rate over a sliding window. To collapse the high-cardinality labels (uri, method, status, instance, exception) down to just the dimension you care about, you need an aggregation operator with a `by` clause. Combining those two ideas gives the query.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The metric base name is `http_server_requests_seconds`. The counter series Prometheus exposes is `http_server_requests_seconds_count`. The PromQL functions are `rate(series[window])` and `sum(...) by (label)`. Window of `1m` is a reasonable starting choice for a dashboard. The Grafana panel type is "Time series." The unit selector under `Standard options → Unit` includes `reqps` (requests per second). Use `legendFormat` to show only the URI label rather than every label combination.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

PromQL shape: `sum(rate(http_server_requests_seconds_count[1m])) by (uri)`. The order matters — `rate` must wrap the raw counter before `sum` aggregates. Panel: Time series, single target, expr as above, `legendFormat` of `{{uri}}`. Unit: `reqps`. If you see "No data," confirm the Prometheus datasource UID in the Grafana panel matches the provisioned UID (`prometheus-ds` in the canonical setup) and that Prometheus is actually scraping your app — `up{job="recon-service"}` should be 1.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open Grafana, click "Add panel" on the ReconX overview dashboard.
2. Pick panel type "Time series" and the Prometheus datasource.
3. Paste the PromQL below as the query.
4. Set `legendFormat` to `{{uri}}` and unit to `reqps`.
5. Exercise the API (a few GET/POST calls); confirm one line per URI renders.
6. Save the dashboard JSON to `monitoring/grafana/provisioning/dashboards/reconx-overview.json`.

**Reference solution** — PromQL:

```promql
sum(rate(http_server_requests_seconds_count[1m])) by (uri)
```

Panel JSON fragment (`monitoring/grafana/provisioning/dashboards/reconx-overview.json` — trainer copy entry):

```json
{
  "type": "timeseries",
  "title": "API request rate by endpoint (TICKET-ADV087)",
  "datasource": { "type": "prometheus", "uid": "reconx-prometheus" },
  "targets": [
    { "expr": "sum(rate(http_server_requests_seconds_count[1m])) by (uri)", "legendFormat": "{{uri}}" }
  ],
  "gridPos": { "h": 8, "w": 12, "x": 0, "y": 0 }
}
```

The provisioned datasource uid is `reconx-prometheus` (see `monitoring/grafana/provisioning/datasources/prometheus.yml`). Set the panel's standard-options unit to `reqps` in the Grafana UI — the trainer-copy JSON keeps panel JSON minimal and relies on the UI for unit selection.

</details>

**▶ Run the project — verify TICKET-ADV087 end-to-end**

Boot the observability stack and confirm the request-rate panel renders one line per URI.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# in another terminal — exercise a few endpoints to get non-zero traffic:
for i in 1 2 3 4 5; do curl -s http://localhost:8080/actuator/health > /dev/null; done
# open http://localhost:3000  (admin/admin)
```

**Observe:**

- The "ReconX Overview" dashboard loads and the "API request rate by endpoint" panel renders one line per `uri`.
- Y-axis unit is `reqps` and the legend reads as a URI (not a `{instance=...,job=...}` blob).
- If the panel shows "No data," check `up{job="recon-service"}` in Prometheus first — the break is almost always at the scrape, not the panel.

---

### TICKET-ADV088 — Grafana panel: API response time P50/P95/P99

**Goal:** A three-line time-series panel showing median, 95th, and 99th percentile latency for each endpoint.

**What**
- A Grafana time-series panel with three queries — P50, P95, P99 — each shaped `histogram_quantile(<q>, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))` with the unit set to seconds.

**Why**
- Latency percentiles are the second pane every on-call opens, and the P95 query here is the exact expression the ADV094 alert rule will compare against the 500 ms threshold.

**Observe**
- After exercising the API, three labelled lines (P50/P95/P99) render with non-zero values and Grafana auto-scales the axis to ms or us.

**Done when:**
- Panel shows three labelled lines: P50, P95, P99.
- Lines are non-zero after you exercise the API.
- The axis renders in human-readable time units (ms/µs scale automatically).

<details>
<summary>Hint 1 — gentle direction</summary>

You cannot compute a percentile from a counter or a rate alone — you need the bucketed histogram data Spring Boot emits automatically for `http_server_requests_seconds`. The PromQL function that interpolates a percentile across bucket boundaries is the histogram quantile function. It requires the `le` bucket-boundary label to be present in the aggregation grouping; if you drop `le` in a `by` clause, the function has nothing to interpolate.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The bucket series is `http_server_requests_seconds_bucket`. The function is `histogram_quantile(q, bucket_rate_expr)` where `q` is between 0 and 1. The inner expression follows the same shape as TICKET-ADV087 but with two changes: target the `_bucket` series, and the `by` clause must include `le` (and any other labels you want to slice by — `uri` is a sensible second). Window of `5m` is a common dashboard choice — wide enough to smooth, narrow enough to react. Set the Grafana axis unit to `s` (seconds) and Grafana will format ms/µs as appropriate.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Three queries on one panel. Each follows the shape `histogram_quantile(Q, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))` with `Q` of `0.50`, `0.95`, `0.99` respectively. `legendFormat` per query: `P50 {{uri}}`, `P95 {{uri}}`, `P99 {{uri}}`. If a percentile is suspiciously flat-zero, recheck that the bucket series exists in `/actuator/prometheus` — without `publishPercentileHistogram` upstream you cannot get this output (this is why the talking point on TICKET-ADV084 matters: the same lesson applies in reverse to the auto-instrumented HTTP timer, which Spring Boot enables out of the box).

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Confirm `management.metrics.distribution.percentiles-histogram.http.server.requests: true` is set in `application.yml` (already on by default in the trainer copy).
2. Add a new Grafana "Time series" panel.
3. Add three query rows A/B/C, each with the histogram_quantile PromQL below (Q = 0.50, 0.95, 0.99).
4. Set `legendFormat` per query: `P50 {{uri}}`, `P95 {{uri}}`, `P99 {{uri}}`.
5. Set the panel unit to `s` (seconds) — Grafana renders ms/µs automatically.
6. Exercise the API; confirm three labelled lines render.

**Reference solution** — PromQL (three queries on one panel):

```promql
histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))
```

Legend formats: `P50 {{uri}}`, `P95 {{uri}}`, `P99 {{uri}}`. Unit: `s`.

The trainer copy's shipped `reconx-overview.json` currently provisions the P95 line only — extend to P50/P99 either through the Grafana UI or by appending two more `targets` entries to the existing panel block:

```json
{
  "type": "timeseries",
  "title": "API P95 latency (TICKET-ADV088)",
  "datasource": { "type": "prometheus", "uid": "reconx-prometheus" },
  "targets": [
    { "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))", "legendFormat": "{{uri}}" }
  ],
  "gridPos": { "h": 8, "w": 12, "x": 12, "y": 0 }
}
```

</details>

**▶ Run the project — verify TICKET-ADV088 end-to-end**

Confirm the P50/P95/P99 latency panel renders three labelled lines once the bucket series exist.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
for i in $(seq 1 20); do curl -s http://localhost:8080/actuator/health > /dev/null; done
# open http://localhost:3000 → ReconX Overview → "API response time P50/P95/P99"
```

**Observe:**

- Three labelled lines (P50, P95, P99) render with non-zero values after a bit of traffic.
- Panel unit is `s` and Grafana auto-formats values into ms or µs.
- If a percentile line is flat at zero, the `_bucket` series is missing — confirm `management.metrics.distribution.percentiles-histogram.http.server.requests: true` is set.

---

### TICKET-ADV089 — Grafana panel: `trade_created_total` over time

**Goal:** A time-series panel showing trades-per-second created across the system.

**What**
- A Grafana time-series panel running `sum(rate(trade_created_total[1m]))` with the legend renamed to "trades/sec" and the unit set to req/s.

**Why**
- Visualising the ADV083 counter as a rate is what turns a monotonic number into the "are trades flowing right now" view used during the ADV097 load test and Day 10 demo.

**Observe**
- After a burst of `POST /api/v1/trades` calls the line rises above zero, and stopping the writes brings it back down within one scrape interval.

**Done when:**
- Panel renders a non-flat line after you POST a handful of trades.
- The legend reads as a trade-create rate, not a raw cumulative count.
- The unit is trades-per-second.

<details>
<summary>Hint 1 — gentle direction</summary>

This is the same `rate(counter[window])` pattern from TICKET-ADV087 but on your custom counter from TICKET-ADV083 instead of the framework's HTTP counter. If you want a cumulative count instead of a rate, you can panel the raw counter directly — but for an SRE-style "are trades flowing right now" view, the rate is what matters.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The metric name is `trade_created_total` (the same name you registered in TICKET-ADV083 — type it exactly). Wrap with `rate(...[1m])`, then `sum(...)` (no `by` clause needed unless you decide to add tags like region or instance later). For unit, Grafana accepts a custom unit string — type `trades/s` in the unit selector. Panel type: Time series, single query.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

PromQL: `sum(rate(trade_created_total[1m]))`. Legend format: `Trades created /s`. Unit: custom string `trades/s`. If you want a cumulative-count variant alongside, add a second query with just `trade_created_total` and switch the panel options to "Increase" calculation — but the rate form is the canonical one for alerting and dashboards.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a new Grafana panel, type "Time series", Prometheus datasource.
2. Paste the PromQL below as a single query.
3. Set `legendFormat` to `Trades created /s`.
4. In the unit selector, type the custom string `trades/s`.
5. POST a handful of trades; confirm the line moves non-zero.

**Reference solution** — PromQL:

```promql
sum(rate(trade_created_total[1m]))
```

Legend format: `Trades created /s`. Unit: `trades/s` (custom unit string set in the Grafana UI).

Trainer dashboard JSON entry (`monitoring/grafana/provisioning/dashboards/reconx-overview.json`):

```json
{
  "type": "timeseries",
  "title": "Trades created / sec (TICKET-ADV089)",
  "datasource": { "type": "prometheus", "uid": "reconx-prometheus" },
  "targets": [{ "expr": "sum(rate(trade_created_total[1m]))" }],
  "gridPos": { "h": 8, "w": 12, "x": 6, "y": 8 }
}
```

Cumulative-count alternative (panel options → calculation "Increase"):

```promql
trade_created_total
```

</details>

**▶ Run the project — verify TICKET-ADV089 end-to-end**

Confirm the trades-created-per-second panel moves when you POST trades.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in $(seq 1 10); do
  curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -X POST http://localhost:8080/api/v1/trades \
    -d "{\"tradeRef\":\"T-ADV089-$i\",\"instrumentSymbol\":\"SAP.DE\",\"counterpartyId\":1,\"quantity\":100,\"price\":245.5,\"tradeDate\":\"2026-06-02\"}"
done
# open http://localhost:3000 → ReconX Overview → "Trades created / sec"
```

**Observe:**

- The "Trades created / sec" panel shows a non-flat line during/after the POST burst.
- Legend reads "Trades created /s" and the unit is `trades/s`.
- If the line is flat at zero but `trade_created_total` is visibly rising on `/actuator/prometheus`, the PromQL is wrapping the wrong series — confirm you used `rate(...[1m])` over the `_total` counter.

---

### TICKET-ADV090 — Grafana histogram panel for `reconciliation_duration_seconds`

**Goal:** Visualise the distribution of recon engine latencies as a heatmap (density over time).

**What**
- A Grafana heatmap panel running `sum by (le) (rate(reconciliation_duration_seconds_bucket[1m]))` with the panel format set to Heatmap and the Y-axis labelled by bucket boundary.

**Why**
- A heatmap surfaces bimodal recon latencies (e.g., fast cache-hit runs vs. slow DB-scan runs) that a single P95 line in ADV088 would average away.

**Observe**
- After triggering a dozen reconciliations the heatmap cells colour by frequency with bucket boundaries on Y and time on X.

**Done when:**
- Heatmap panel renders with cells coloured by frequency after you trigger a dozen reconciliations.
- The Y-axis represents bucket boundaries.
- The X-axis represents time.

<details>
<summary>Hint 1 — gentle direction</summary>

A heatmap in Grafana takes a series of bucket rates (one per `le` boundary) and renders cells whose colour intensity reflects how often values fell into each bucket. The PromQL shape is the same `rate` over `_bucket`, grouped only by `le`. Set the Grafana panel's format to "Heatmap" so Grafana knows how to interpret the bucket dimension.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Panel type: Heatmap. Query: rate of the `_bucket` series of `reconciliation_duration_seconds` over a 5-minute window, summed by `le`. In the panel's Query options, set "Format" to `Heatmap`. As a simpler alternative if the heatmap is overwhelming visually, build the same three-line P50/P95/P99 panel you built in TICKET-ADV088, but pointed at the recon timer's bucket series. Both are valid; the heatmap shows distribution shape, the line shows percentile evolution.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Heatmap query: `sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le)`. Format: Heatmap. Three-line alternative: three `histogram_quantile(Q, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))` queries for Q in `{0.50, 0.95, 0.99}`. If the heatmap shows a single dark row at the top, your engine call is faster than the smallest bucket boundary — you may need to trigger a slower workload to see the distribution, or accept that "always fast" is a valid observation.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a new Grafana panel and set type to "Heatmap".
2. Paste the heatmap PromQL below.
3. In the panel's Query options, set "Format" to `Heatmap`.
4. Trigger a dozen reconciliations from `POST /api/v1/recon/run`.
5. Confirm cells appear, coloured by frequency, with `le` boundaries on the Y-axis.
6. (Optional) Add the three-line percentile alternative as a second panel for percentile-evolution visibility.

**Reference solution** — PromQL:

```promql
sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le)
```

The trainer-copy provisioned dashboard ships this as a `timeseries` (per-`le` bucket lines) for simplicity; switch the panel type to **Heatmap** in the Grafana UI and set Query options → Format = `Heatmap` to get the density rendering.

Trainer dashboard JSON entry (`monitoring/grafana/provisioning/dashboards/reconx-overview.json`):

```json
{
  "type": "timeseries",
  "title": "Reconciliation duration histogram (TICKET-ADV090)",
  "datasource": { "type": "prometheus", "uid": "reconx-prometheus" },
  "targets": [{ "expr": "sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le)" }],
  "gridPos": { "h": 8, "w": 24, "x": 0, "y": 16 }
}
```

Three-line percentile alternative (one panel, three queries):

```promql
histogram_quantile(0.50, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.95, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))
histogram_quantile(0.99, sum(rate(reconciliation_duration_seconds_bucket[5m])) by (le))
```

</details>

**▶ Run the project — verify TICKET-ADV090 end-to-end**

Confirm the reconciliation-duration heatmap (or percentile lines) renders after triggering a dozen recon runs.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
for i in $(seq 1 12); do
  curl -s -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/v1/recon/run
done
# open http://localhost:3000 → ReconX Overview → "Reconciliation duration histogram"
```

**Observe:**

- Heatmap cells (or three percentile lines) appear with non-empty data; Y-axis shows `le` bucket boundaries.
- The PromQL preview in the panel editor shows one series per `le` value.
- If you see only a single dark row at the top of the heatmap, the engine is faster than the smallest bucket — trigger a heavier workload or accept "always fast" as a valid observation.

---

### TICKET-ADV091 — Grafana stat panel: `recon_break_count`

**Goal:** A single big-number tile showing the current open-break count, coloured by severity.

**What**
- A Grafana stat panel bound to `recon_break_count` with thresholds green < 10, yellow 10-50, red > 50 and unit set to short.

**Why**
- One glance has to tell the on-call whether breaks are fine, warming up, or paging-worthy — the thresholds here mirror the 50-break boundary the ADV093 alert rule fires on.

**Observe**
- Closing a break in Postgres drops the displayed number within one scrape interval, and pushing the count above 50 turns the tile red.

**Done when:**
- Stat panel renders the current break count as a single large number.
- The colour changes by threshold: green at low values, yellow at mid, red at high.
- Closing a break in the database visibly drops the displayed number within one scrape interval.

<details>
<summary>Hint 1 — gentle direction</summary>

A Gauge maps cleanly to a Grafana Stat panel — one current value, no rate, no aggregation needed. The interesting part is the thresholds: a panel without them is just a number; a panel with them tells an operator at a glance whether the situation is fine, warming up, or on fire. Think about what break counts would worry a human, and divide your colour bands accordingly.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Panel type: Stat. PromQL is the raw metric name — no `rate`, no aggregation. Under `Thresholds`, define three steps: a green base, a yellow boundary (around 10), a red boundary (around 50). Set the Stat panel's "Color mode" to "Value" so the displayed number itself takes the threshold colour.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Query: `recon_break_count`. Thresholds: green `0`, yellow `10`, red `50`. Color mode: Value. The same threshold values will reappear in TICKET-ADV093 as the alert rule — keeping panel and alert thresholds aligned means an operator looking at the panel can predict the alert and vice versa. Standardise it now.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a new Grafana panel and set type to "Stat".
2. Use the raw metric `recon_break_count` as the only query (no `rate`, no aggregation).
3. Under `Thresholds`, define three steps: green at `0`, yellow at `10`, red at `50`.
4. Set the Stat panel's "Color mode" to `Value` so the number itself takes the threshold colour.
5. Open a break in the DB; confirm the tile turns yellow/red as the count crosses thresholds.

**Reference solution** — PromQL:

```promql
recon_break_count
```

Panel type: Stat. Color mode: Value. Thresholds (set via Grafana UI; trainer JSON keeps the panel minimal):

- Green: 0 – 10
- Yellow: 10 – 50
- Red: > 50

Trainer dashboard JSON entry (`monitoring/grafana/provisioning/dashboards/reconx-overview.json`):

```json
{
  "type": "stat",
  "title": "Open recon breaks (TICKET-ADV091)",
  "datasource": { "type": "prometheus", "uid": "reconx-prometheus" },
  "targets": [{ "expr": "recon_break_count" }],
  "gridPos": { "h": 4, "w": 6, "x": 0, "y": 8 }
}
```

</details>

**▶ Run the project — verify TICKET-ADV091 end-to-end**

Confirm the stat tile renders the current open-break count and changes colour by threshold.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# open http://localhost:3000 → ReconX Overview → "Open recon breaks"
# in psql:  INSERT INTO recon_breaks (...) VALUES (..., 'OPEN'); -- bump count past 10 then past 50
```

**Observe:**

- Stat panel shows a single large number matching the `recon_break_count` gauge.
- Inserting OPEN breaks crosses the 10 and 50 thresholds and the tile turns yellow then red.
- Resolving breaks drops the value within one scrape interval (~15s); if the colour does not change at all, the Stat panel's Color mode is set to `Background` instead of `Value`.

---

### TICKET-ADV092 — Grafana pie chart: trades by status

**Goal:** A pie chart showing the current breakdown of trades across statuses (PENDING, MATCHED, UNMATCHED, DISPUTED, CANCELLED).

**What**
- A Grafana pie chart fed by five `Gauge` registrations tagged `status=PENDING|MATCHED|UNMATCHED|DISPUTED|CANCELLED`, each pulling its count from `tradeRepository.countByStatus(...)`.

**Why**
- The ADV083 counter only fires at create time and cannot reflect later status transitions — this per-status gauge is the only honest way to chart current state distribution.

**Observe**
- Flipping a trade from PENDING to MATCHED via the API shifts the slice proportions within one scrape interval, and each slice label shows the status name and count.

**Done when:**
- Pie chart renders with one slice per status.
- Slice labels show the status name and count.
- Changing a trade's status and re-scraping shifts the proportions within one scrape interval.

<details>
<summary>Hint 1 — gentle direction</summary>

Your `trade_created_total` counter (TICKET-ADV083) has no `status` tag — and it should not, because the moment a trade is created it is in one status (PENDING) and incrementing five differently-tagged counters at creation time would not reflect later transitions. You need a separate metric whose value reflects the *current* tally per status. That sounds like a Gauge, with one registration per status, each tagged with the status name, each pulling its count from a repository query.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Add a `countByStatus(String status)` method on `TradeRepository` (Spring Data can derive it from the method name, or write a `@Query`). Register one `Gauge` per status value in a small `@Component` constructor — loop over the list of statuses and call `Gauge.builder("trades_by_status", repo, r -> r.countByStatus(status)).tag("status", status).register(registry)`. The metric name stays the same across all registrations; the `status` tag differentiates the series. The Grafana panel type is Pie chart. The PromQL aggregates with `sum by (status)`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Component class: `TradesByStatusGauge`. Constructor loops over `List.of("PENDING","MATCHED","UNMATCHED","DISPUTED","CANCELLED")` and registers a gauge for each, tagged `status=<that-status>`. Repository: `long countByStatus(String status)`. PromQL: `sum by (status) (trades_by_status)`. Panel type: Pie chart. Legend format: `{{status}}`. If a status has no trades, that gauge reports 0 and the slice does not render — expected, not a bug.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `long countByStatus(String status)` to `TradeRepository` (Spring Data derives the query from the name).
2. Create `backend/src/main/java/com/dbtraining/reconx/observability/TradesByStatusGauge.java` annotated `@Component`.
3. In the constructor, loop over the five statuses and register one Gauge per status with `.tag("status", status)`.
4. Restart the app; confirm `/actuator/prometheus` shows five `trades_by_status{status="..."}` series.
5. Add a Grafana "Pie chart" panel with PromQL `sum by (status) (trades_by_status)` and legend `{{status}}`.

**Reference solution** — this exercise is not pre-built in the trainer codebase; the trainer-guide markdown sketch is the canonical reference. `TradeRepository` already exposes `long countByStatus(String status)` — feed it from a new gauge component.

`backend/src/main/java/com/dbtraining/reconx/observability/TradesByStatusGauge.java`:

```java
package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.repository.TradeRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TradesByStatusGauge {

    public TradesByStatusGauge(MeterRegistry registry, TradeRepository repo) {
        for (String status : List.of("PENDING","MATCHED","UNMATCHED","DISPUTED","CANCELLED")) {
            Gauge.builder("trades_by_status", repo, r -> r.countByStatus(status))
                 .tag("status", status)
                 .description("Trades currently in a given status")
                 .register(registry);
        }
    }
}
```

PromQL for the pie chart panel:

```promql
sum by (status) (trades_by_status)
```

Grafana panel type: **Pie chart**. Legend: `{{status}}`.

</details>

**▶ Run the project — verify TICKET-ADV092 end-to-end**

Confirm one tagged gauge series appears per trade status and the Grafana pie chart shows slices for each.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
curl -s http://localhost:8080/actuator/prometheus | grep trades_by_status
# open http://localhost:3000 → ReconX Overview → "Trades by status" pie chart
```

**Observe:**

- `/actuator/prometheus` lists five `trades_by_status{status="..."}` series, one per status value.
- The pie chart in Grafana renders one slice per non-zero status with `{{status}}` as the legend.
- If only some slices render, that is expected — statuses with a zero count emit `0.0` and Grafana hides the slice; not a bug.

---

### TICKET-ADV093 — Alert: `recon_break_count > 50` for 5 min

**Goal:** A Prometheus alert rule that fires when there have been more than 50 open breaks continuously for 5 minutes.

**What**
- A Prometheus alert rule `HighOpenBreakCount` in `prometheus/rules/recon-alerts.yml` with `expr: recon_break_count > 50`, `for: 5m`, `labels: { severity: warning }`, and a `summary` annotation.

**Why**
- Pages the on-call only when the ADV085 gauge stays elevated — the 5-minute `for` clause suppresses transient scrape spikes that would otherwise wake people up.

**Observe**
- After inflating open breaks past 50 for five minutes the rule appears at `http://localhost:9090/rules` and a firing instance shows up on `/alerts`.

**Done when:**
- The rule appears under `http://localhost:9090/rules`.
- Manually inflating the open-break count and waiting fires the alert (visible under `/alerts`).
- The alert has a `severity` label and a `summary` annotation.

<details>
<summary>Hint 1 — gentle direction</summary>

Prometheus alert rules live in a YAML file referenced from `prometheus.yml` under `rule_files`. A rule has a name, an expression (any PromQL that returns a scalar or vector), a `for` duration that requires the expression to stay true continuously before firing, optional labels (used by Alertmanager for routing), and optional annotations (free-text for the human who reads the alert). The `for` clause is what protects you from paging on every transient scrape spike.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Create `monitoring/prometheus/alert.rules.yml` if it does not exist. Reference it from `prometheus.yml` under `rule_files: ["alert.rules.yml"]`. The rule file has a top-level `groups:` list; each group has a `name`, an `interval`, and a `rules:` list. Each rule has `alert`, `expr`, `for`, `labels`, `annotations`. The annotation `description` can interpolate the current value with `{{ $value }}`. After saving, restart (or hot-reload) Prometheus and check `http://localhost:9090/rules` — your rule must appear in the list.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Alert name: `TooManyReconBreaks`. Expression: the gauge metric directly compared against the threshold scalar (50). Duration: `5m`. Labels: `severity: warning`, plus a team label (e.g. `team: recon-ops`) to make Alertmanager routing easy later. Annotations: `summary` (one-line subject) and `description` (longer triage hint, interpolating `{{ $value }}`). After the rule loads, confirm by lowering the threshold temporarily to a value below the current count — the alert should move to PENDING and after 5 minutes to FIRING.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `monitoring/prometheus/alerts.yml` with a top-level `groups:` list.
2. Add a group `reconx-business` with a rule named `TooManyReconBreaks`, expression `recon_break_count > 50`, `for: 5m`.
3. Add `severity: warning` label and a `summary` + `description` annotation interpolating `{{ $value }}`.
4. Reference the file from `monitoring/prometheus/prometheus.yml` under `rule_files: ["alerts.yml"]`.
5. Restart Prometheus (or hit `POST /-/reload`).
6. Visit `http://localhost:9090/rules` and confirm `TooManyReconBreaks` appears.
7. Lower the threshold temporarily; confirm it moves to PENDING then FIRING.

**Reference solution** (`monitoring/prometheus/alerts.yml`):

```yaml
# TICKET-ADV093 / TICKET-ADV094 — Prometheus alert rules
groups:
  - name: reconx-business
    rules:
      - alert: TooManyReconBreaks
        expr: recon_break_count > 50
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Open recon breaks > 50 for 5 min"
          description: "Current breaks: {{ $value }}"
```

`monitoring/prometheus/prometheus.yml` (reference snippet):

```yaml
rule_files:
  - "alerts.yml"
```

</details>

**▶ Run the project — verify TICKET-ADV093 end-to-end**

Confirm the alert rule loads in Prometheus, fires when breaches persist for the `for:` window, and carries severity labels.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# trigger the condition: inject enough OPEN breaks (e.g. via SQL or by ingesting bad reconciliation data)
# then open http://localhost:9090/rules and http://localhost:9090/alerts
```

**Observe:**

- `TooManyReconBreaks` is listed at `http://localhost:9090/rules` with the configured `expr`, `for`, labels, and annotations.
- After breaching `> 50` for 5 minutes, the alert moves to PENDING then FIRING on `/alerts`; severity label reads `warning`.
- If the rule never appears in `/rules`, `rule_files:` in `prometheus.yml` is misconfigured or Prometheus did not reload — check the Prometheus container logs for YAML parse errors.

---

### TICKET-ADV094 — Alert: API P95 latency > 500ms for 3 min

**Goal:** A Prometheus alert rule on P95 HTTP latency exceeding 500 ms continuously for 3 minutes.

**What**
- A Prometheus alert rule `HighApiP95Latency` reusing the ADV088 query: `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m]))) > 0.5`, `for: 3m`, with the current P95 value templated into the annotation.

**Why**
- Catches latency regressions faster than the break-count alert (3 min vs 5) because user-visible slowness is the signal that drains trust the quickest, and templating the value spares the on-call a PromQL round-trip.

**Observe**
- Throttling an endpoint for three-plus minutes pushes the alert into the firing state on `/alerts`, and the annotation reads e.g. `current P95 = 0.612s`.

**Done when:**
- The rule appears under `http://localhost:9090/rules`.
- Running a slow endpoint (or an artificially throttled one) for 3+ minutes fires the alert.
- The alert annotation includes the current P95 value.

<details>
<summary>Hint 1 — gentle direction</summary>

This rule reuses the same PromQL shape as TICKET-ADV088 — a `histogram_quantile` over the HTTP bucket series — but compared against a numeric threshold. The threshold is in seconds because the metric base unit is seconds. The `for` window is shorter than the break-count alert (3 minutes vs 5) because latency is a faster-moving signal and you want to react sooner.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Append a second rule to the same `alert.rules.yml` group from TICKET-ADV093. The expression wraps the P95 PromQL from TICKET-ADV088 (without the `uri` dimension, unless you want per-endpoint alerts) and adds `> 0.5` for "above 500 ms." Use the 5-minute `rate` window from TICKET-ADV088 for consistency. Labels: `severity: warning`, `team: backend`. Annotations: same shape as TICKET-ADV093, with the P95 value interpolated.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Alert name: `HighApiLatency`. Expression: `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) > 0.5`. Duration: `3m`. Labels and annotations follow the TICKET-ADV093 pattern. Test: temporarily lower the threshold to `> 0.0001` and confirm the alert moves to PENDING then FIRING; revert after the test. Production trade-off note for the debrief: Prometheus alerts survive Grafana being down, Grafana alerts do not — keep the alert in Prometheus and visualise it in Grafana.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `monitoring/prometheus/alerts.yml` from TICKET-ADV093.
2. Append a second rule to the same `reconx-business` group, named `HighApiLatency`.
3. Use the P95 PromQL from TICKET-ADV088 (without the `uri` dimension) and `> 0.5` as the threshold.
4. Set `for: 3m`, labels `severity: warning` and `team: backend`, plus `summary`/`description` annotations.
5. Reload Prometheus and confirm both rules appear under `http://localhost:9090/rules`.
6. Test by lowering the threshold to `> 0.0001` temporarily; confirm PENDING then FIRING; revert.

**Reference solution** (`monitoring/prometheus/alerts.yml`, appended to the same `reconx-business` group):

```yaml
      - alert: HighApiLatencyP95
        expr: |
          histogram_quantile(0.95,
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri)
          ) > 0.5
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "P95 request latency > 500ms for 3 min on {{ $labels.uri }}"
```

The trainer copy keeps `by (le, uri)` in the aggregation so the alert fires per-endpoint and the annotation surfaces the offending `uri` via `{{ $labels.uri }}`. The same `alerts.yml` file also ships a third rule (`KafkaDlqGrowing`) that students will reach on Day 5/6 if they push DLQ work further.

</details>

**▶ Run the project — verify TICKET-ADV094 end-to-end**

Confirm the latency alert appears alongside the break-count rule and fires when P95 stays high for the `for:` window.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
# slow a service method (e.g. Thread.sleep in a handler) or hammer a heavy endpoint
ab -n 500 -c 20 http://localhost:8080/api/v1/instruments/1
# then open http://localhost:9090/rules and http://localhost:9090/alerts
```

**Observe:**

- Both `TooManyReconBreaks` and `HighApiLatencyP95` appear under `http://localhost:9090/rules`.
- After 3 minutes of P95 above 500ms, `HighApiLatencyP95` moves to FIRING with the offending `uri` interpolated in the annotation.
- If the alert never moves to PENDING, the underlying `histogram_quantile` query is returning `NaN` — confirm the HTTP histogram exposure flag is on and the bucket series exist.

---

### Workshop 6D — Custom Spring Boot Starter, JMX, performance test

The closing block packages a cross-cutting concern as a starter (so future ReconX microservices get audit publishing for free), exposes a runtime-tunable knob through JMX, and finishes with a small load test that you can watch live on the dashboard you have just built.

### TICKET-ADV095 — Custom Spring Boot Starter: `recon-audit-starter`

**Goal:** Package an audit-event publisher as a reusable Spring Boot starter that auto-configures itself when added to a consumer's classpath.

**What**
- A separate Maven module `recon-audit-starter` containing `AuditAutoConfiguration` (gated by `@ConditionalOnProperty("reconx.audit.enabled", matchIfMissing=true)`) and a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file listing it.

**Why**
- Demonstrates the same auto-configuration mechanism Spring Boot itself uses, so the audit publisher built on Day 9 can be reused by sister services without copy-pasting wiring.

**Observe**
- Adding the starter dependency wires the bean automatically; setting `reconx.audit.enabled=false` in `application.yml` and restarting shows the bean disappearing from `/actuator/beans`.

**Done when:**
- A new Maven module `recon-audit-starter` builds independently of the main app.
- The main `recon-service` depends on it via a single Maven coordinate.
- Removing the dependency removes the auto-wired bean cleanly; adding it back re-enables it.
- A `reconx.audit.enabled=false` property in the consumer disables the auto-configuration.

<details>
<summary>Hint 1 — gentle direction</summary>

A Spring Boot starter is a *separate Maven module* that other projects pull in as a dependency. Its job is to provide one or more `@AutoConfiguration` classes that register beans only under certain conditions (consumer has the right classes on the classpath, consumer hasn't already defined the bean themselves, consumer hasn't disabled the feature via a property). The conditional annotations are what make a starter polite — silent when not wanted, automatic when wanted.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Module layout: a sibling Maven module (not a sub-package) with its own `pom.xml`, depending on `spring-boot-autoconfigure`. Inside the module, create three classes: an `@AutoConfiguration` class, a `@ConfigurationProperties("reconx.audit")` class for the consumer to configure, and the publisher class itself. The auto-configuration class is annotated `@AutoConfiguration`, `@ConditionalOnClass(ApplicationEventPublisher.class)`, `@ConditionalOnProperty(prefix="reconx.audit", name="enabled", havingValue="true", matchIfMissing=true)`, `@EnableConfigurationProperties(AuditProperties.class)`, and defines an `@Bean` annotated `@ConditionalOnMissingBean`. **Boot 3 has changed the discovery file** — it is no longer `META-INF/spring.factories`. The correct path is `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, a plain-text file with one fully-qualified class name per line. Get this wrong and the starter installs but never activates.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Module artifactId: `recon-audit-starter`. Three Java classes: `AuditAutoConfiguration` (class-level `@AutoConfiguration`, `@ConditionalOnClass`, `@ConditionalOnProperty`, `@EnableConfigurationProperties`; one `@Bean @ConditionalOnMissingBean` method returning `AuditEventPublisher`), `AuditProperties` (`@ConfigurationProperties("reconx.audit")` with an `enabled` boolean and a `topic` string, plus getters/setters), `AuditEventPublisher` (constructor takes `ApplicationEventPublisher` and `AuditProperties`). Discovery file path: exactly `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Its contents: the fully-qualified name of `AuditAutoConfiguration`, one line, no comments needed. Verify after building with `jar tf target/recon-audit-starter-1.0.0.jar | grep AutoConfiguration.imports` — if the file is missing from the JAR, the starter is dead on arrival.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create a sibling Maven module `recon-audit-starter/` (not a sub-package) with its own `pom.xml`.
2. Add `spring-boot-autoconfigure` + `spring-boot-configuration-processor` dependencies.
3. Create the three Java classes: `AuditAutoConfiguration`, `AuditProperties`, `AuditEventPublisher`.
4. Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` with one line: the FQN of `AuditAutoConfiguration`.
5. Build with `./mvnw install` and verify with `jar tf target/recon-audit-starter-1.0.0.jar | grep AutoConfiguration.imports`.
6. Add the starter dependency to the main `recon-service` pom; restart and confirm `AuditEventPublisher` is auto-wired.
7. Set `reconx.audit.enabled=false` in the consumer and confirm the bean disappears.

**Reference solution** — this exercise is not pre-built in the trainer codebase (the trainer copy ships as a single runnable Maven module so the answer key boots cleanly); the trainer-guide markdown is the canonical reference for the module layout and contents.

Module layout:

```
recon-audit-starter/
├── pom.xml
└── src/main/
    ├── java/com/dbtraining/reconx/audit/
    │   ├── AuditAutoConfiguration.java
    │   ├── AuditEventPublisher.java
    │   └── AuditProperties.java
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

`recon-audit-starter/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.dbtraining.reconx</groupId>
    <artifactId>recon-audit-starter</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

`recon-audit-starter/src/main/java/com/dbtraining/reconx/audit/AuditAutoConfiguration.java`:

```java
package com.dbtraining.reconx.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ApplicationEventPublisher.class)
@ConditionalOnProperty(prefix = "reconx.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditEventPublisher auditEventPublisher(ApplicationEventPublisher publisher,
                                                    AuditProperties props) {
        return new AuditEventPublisher(publisher, props);
    }
}
```

`recon-audit-starter/src/main/java/com/dbtraining/reconx/audit/AuditProperties.java`:

```java
package com.dbtraining.reconx.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("reconx.audit")
public class AuditProperties {
    private boolean enabled = true;
    private String topic = "audit-events";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
}
```

`recon-audit-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.dbtraining.reconx.audit.AuditAutoConfiguration
```

Consumer side (`backend/pom.xml`):

```xml
<dependency>
    <groupId>com.dbtraining.reconx</groupId>
    <artifactId>recon-audit-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

</details>

**▶ Run the project — verify TICKET-ADV095 end-to-end**

Confirm the starter module builds standalone, installs to your local Maven repo, and the imports file ships inside the JAR.

```bash
cd recon-audit-starter
../mvnw clean install
jar tf target/recon-audit-starter-1.0.0.jar | grep AutoConfiguration.imports
ls -la ~/.m2/repository/com/dbtraining/reconx/recon-audit-starter/1.0.0/
# then in the consumer:
cd ../backend && ../mvnw spring-boot:run
# toggle off and confirm the bean disappears:
SPRING_APPLICATION_JSON='{"reconx":{"audit":{"enabled":false}}}' ../mvnw spring-boot:run
```

**Observe:**

- The starter's JAR appears in `~/.m2/repository/com/dbtraining/reconx/recon-audit-starter/1.0.0/`.
- `jar tf` shows `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` inside the JAR.
- With the starter on the classpath the `AuditEventPublisher` bean is autowired; setting `reconx.audit.enabled=false` makes it disappear cleanly. If the bean never appears, the imports file is at the wrong path (e.g., still under the Boot-2-era `META-INF/spring.factories`).

---

### TICKET-ADV096 — JMX beans: toggle caching, adjust recon tolerance at runtime

**Goal:** Expose a `ReconConfig` MBean that lets an operator change the price tolerance and toggle caching at runtime from JConsole, plus invoke a `clearCache()` operation.

**What**
- A `@Component @ManagedResource(objectName = "reconx:type=ReconConfig")` bean exposing `priceTolerance` as a `@ManagedAttribute` (read/write, `volatile`) and `clearCache()` as a `@ManagedOperation`, with `spring.jmx.enabled=true` in `application.yml`.

**Why**
- Lets ops tune the recon tolerance and flush the ADV081/082 caches without a redeploy — the same lever the trainer will use during the ADV097 load test to demonstrate live config changes.

**Observe**
- JConsole shows `reconx:type=ReconConfig` in the MBeans tree, editing `PriceTolerance` changes the value the next recon run uses, and invoking `clearCache()` empties Caffeine without restart.

**Done when:**
- JConsole connects to the running JVM and shows `reconx:type=ReconConfig` in the MBeans tree.
- Editing `PriceTolerance` from JConsole changes the value the recon engine reads on the next run.
- Invoking `clearCache()` from JConsole empties the Caffeine caches without restarting the app.

<details>
<summary>Hint 1 — gentle direction</summary>

Spring's JMX exposure is *off by default* in Boot 3. You enable it via configuration. Once enabled, Spring scans for beans annotated with the JMX export annotation and registers them with the platform MBean server. Read and write JMX attributes both require an annotation on the corresponding getter and setter; an operation (a method that does something, not just exposes a value) needs a separate annotation. Mutable state in the MBean should be `volatile` because JConsole writes happen on a different thread than the engine reads.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Configuration: `spring.jmx.enabled: true` in `application.yml`. Annotations on the class: `@Component` (so Spring builds it) and `@ManagedResource(objectName="reconx:type=ReconConfig", description="...")`. The `objectName` must use the `domain:type=Name` convention — without `type=`, JConsole renders the bean in the root rather than under a folder. Annotations on attributes: `@ManagedAttribute` on the getter, `@ManagedAttribute` on the setter (both are needed for read-write). Annotation on operations: `@ManagedOperation`. State fields should be `private volatile` and have sensible defaults. `setPriceTolerance` should validate (e.g. between 0 and 1) and throw `IllegalArgumentException` on bad input — JConsole surfaces this to the operator.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Class: `ReconConfigMBean` annotated `@Component` and `@ManagedResource(objectName="reconx:type=ReconConfig", description="Runtime tuning for the reconciliation engine")`. Constructor receives the `CacheManager`. Fields: `private volatile double priceTolerance = 0.01`, `private volatile boolean cachingEnabled = true`, `private final CacheManager cacheManager`. Methods: `@ManagedAttribute double getPriceTolerance()`, `@ManagedAttribute void setPriceTolerance(double v)` (validate 0..1, throw on bad), `@ManagedAttribute boolean isCachingEnabled()`, `@ManagedAttribute void setCachingEnabled(boolean enabled)`, `@ManagedOperation void clearCache()` (iterates `cacheManager.getCacheNames()` and calls `.clear()` on each). The reconciliation engine should read `priceTolerance` from this bean on each run rather than holding its own constant — that is what makes the JMX edit visible without restart.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `spring.jmx.enabled: true` under `spring:` in `application.yml`.
2. Create `backend/src/main/java/com/dbtraining/reconx/observability/ReconConfigMBean.java` annotated `@Component` and `@ManagedResource(objectName="reconx:type=ReconConfig", ...)`.
3. Add `private volatile double priceTolerance` and `private volatile boolean cachingEnabled` state fields with sensible defaults.
4. Annotate getter/setter pairs with `@ManagedAttribute` and validate inputs (throw `IllegalArgumentException` on bad values).
5. Add `@ManagedOperation void clearCache()` iterating `cacheManager.getCacheNames()`.
6. Inject `ReconConfigMBean` into the reconciliation engine and read `priceTolerance` from it on each run.
7. Boot the app, launch JConsole, navigate to `MBeans → reconx → ReconConfig`, edit `PriceTolerance`, invoke `clearCache()`.

**Reference solution** — this MBean is not pre-built in the trainer codebase; the trainer-guide markdown is the canonical reference.

`backend/src/main/java/com/dbtraining/reconx/observability/ReconConfigMBean.java`:

```java
package com.dbtraining.reconx.observability;

import org.springframework.cache.CacheManager;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource(
    objectName = "reconx:type=ReconConfig",
    description = "Runtime tuning for the reconciliation engine"
)
public class ReconConfigMBean {

    private volatile double priceTolerance = 0.01;
    private volatile boolean cachingEnabled = true;
    private final CacheManager cacheManager;

    public ReconConfigMBean(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @ManagedAttribute(description = "Price tolerance for break detection (0.0 - 1.0)")
    public double getPriceTolerance() {
        return priceTolerance;
    }

    @ManagedAttribute
    public void setPriceTolerance(double v) {
        if (v < 0 || v > 1) throw new IllegalArgumentException("tolerance must be 0..1");
        this.priceTolerance = v;
    }

    @ManagedAttribute
    public boolean isCachingEnabled() { return cachingEnabled; }

    @ManagedAttribute
    public void setCachingEnabled(boolean enabled) { this.cachingEnabled = enabled; }

    @ManagedOperation(description = "Evict all entries from the instruments cache")
    public void clearCache() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
    }
}
```

`backend/src/main/resources/application.yml`:

```yaml
spring:
  jmx:
    enabled: true
```

</details>

**▶ Run the project — verify TICKET-ADV096 end-to-end**

Boot with JMX enabled and confirm JConsole can read, write, and invoke operations on the `ReconConfig` MBean.

```bash
./mvnw -pl backend spring-boot:run
# in another terminal:
jconsole
# in JConsole: Local Process → reconx-service PID → MBeans tab → reconx → ReconConfig
```

**Observe:**

- `reconx:type=ReconConfig` is visible under MBeans → reconx; attributes `PriceTolerance`, `CachingEnabled` are listed, and operation `clearCache` is invocable.
- Editing `PriceTolerance` from JConsole changes the value that subsequent reconcile runs use; invoking `clearCache()` empties the Caffeine caches without a restart.
- If the MBean is missing entirely, `spring.jmx.enabled: true` is not set or the bean is missing `@ManagedResource`; if JConsole rejects a `setPriceTolerance` call, the validation throw is doing its job (try a value in 0..1).

---

### TICKET-ADV097 — Performance test: 100 concurrent requests

**Goal:** Drive 100 trade-creation requests at concurrency 10 against the running API and observe the load live on your Grafana panels.

**What**
- An `ab -n 100 -c 10 -H "Authorization: Bearer <jwt>" -p trade.json -T application/json http://localhost:8080/api/v1/trades` run (or k6 equivalent) with the throughput and P95 captured from both the tool output and Grafana.

**Why**
- This is the first time the ADV087/ADV088/ADV089 panels see real load and the only proof before Day 10 that the cache, counter, timer, and gauge wiring actually survive contention.

**Observe**
- All 100 requests return HTTP 201, the request-rate panel spikes during the run, the P95 panel reports a non-trivial value, and the recorded throughput matches `ab`'s `Requests per second` line within a small margin.

**Done when:**
- 100 trades are POSTed successfully (HTTP 201) under the load tool.
- The TICKET-ADV087 request-rate panel visibly spikes during the run.
- The TICKET-ADV088 P95 latency panel reports a non-trivial value during the spike.
- You record the throughput (req/s) and P95 latency from the tool's output and from Grafana.

<details>
<summary>Hint 1 — gentle direction</summary>

A real load test needs concurrency — issuing requests one at a time in a shell `for` loop only measures serial throughput (about one request per second) and tells you nothing about how the system behaves under contention. Pick a tool that can drive concurrent connections: Apache Bench (`ab`) and k6 are both fine here. The interesting variable is not total requests; it is the concurrency level. Authentication: your API requires a JWT, so the load tool needs to send a `Bearer` token with every request.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Apache Bench flags: `-n` total requests, `-c` concurrency, `-H` extra header (for `Authorization: Bearer <token>`), `-T` content-type, `-p` body file. For `ab`, the body file is a JSON file on disk — every request sends the same body, which is fine because your trade entity uses a `tradeRef` that does not need to be unique per request *if* you do not have a unique constraint (check this; if you do, prefer k6 which can template per-iteration). k6 flags: `--vus` virtual users, `--iterations` total iterations, environment variables for the token. Get a token first by hitting the login endpoint and parsing the JSON response with `jq -r .accessToken`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Shell sequence: (1) POST `/api/auth/login` with `trader@db.com`/`trader123`, pipe to `jq -r .accessToken`, store in `TOKEN`. (2) Write a `trade.json` file with the trade payload (tradeRef, instrumentSymbol, counterpartyId, quantity, price, tradeDate). (3) Run `ab -n 100 -c 10 -H "Authorization: Bearer $TOKEN" -T application/json -p trade.json http://localhost:8080/api/v1/trades`. Read off the `Requests per second` line and `Percentage of the requests served within a certain time` table from the output. Cross-check against the Grafana panel — the rate should match within rounding, and the P95 latency Grafana shows should agree with the 95th percentile line `ab` prints. If the run errors out partway, you likely have a unique constraint on `tradeRef` — switch to the k6 script which templates the ref per iteration using `__VU` and `__ITER`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. POST `/api/auth/login` to fetch a JWT; pipe through `jq -r .accessToken` and store in `TOKEN`.
2. Write a `trade.json` file with a sample trade payload.
3. Run `ab -n 100 -c 10` with the Authorization header and the JSON body.
4. Read off the throughput (`Requests per second`) and the P95 latency line from the `ab` output.
5. While the test runs, watch the TICKET-ADV087 and TICKET-ADV088 Grafana panels spike.
6. Record both numbers (tool output + Grafana) and confirm they agree within rounding.
7. If `ab` errors on duplicate `tradeRef`, fall back to the k6 script (templates per-iteration ref).

**Reference solution** — this exercise is a runtime activity (no pre-built shell script in the trainer codebase); the trainer-guide markdown is the canonical reference.

```bash
# Get a token first
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)

# Apache Bench: 100 total requests, 10 concurrent
ab -n 100 -c 10 \
   -H "Authorization: Bearer $TOKEN" \
   -T application/json \
   -p trade.json \
   http://localhost:8080/api/v1/trades
```

`trade.json`:

```json
{
  "tradeRef": "PERF-001",
  "instrumentSymbol": "SAP.DE",
  "counterpartyId": 1,
  "quantity": 100,
  "price": 245.5,
  "tradeDate": "2026-06-02"
}
```

k6 fallback (`perf.js`):

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 10,
  iterations: 100,
};

const TOKEN = __ENV.TOKEN;

export default function () {
  const res = http.post('http://localhost:8080/api/v1/trades',
    JSON.stringify({tradeRef: `K6-${__VU}-${__ITER}`, instrumentSymbol: 'SAP.DE',
                    counterpartyId: 1, quantity: 100, price: 245.5, tradeDate: '2026-06-02'}),
    { headers: { 'Authorization': `Bearer ${TOKEN}`, 'Content-Type': 'application/json' }});
  check(res, { 'status is 201': r => r.status === 201 });
}

// run: TOKEN=$TOKEN k6 run perf.js
```

</details>

**▶ Run the project — verify TICKET-ADV097 end-to-end**

Drive 100 concurrent trade creations and watch the Grafana dashboards spike live.

```bash
docker compose up -d postgres prometheus grafana
./mvnw -pl backend spring-boot:run
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r .accessToken)
ab -n 100 -c 10 \
   -H "Authorization: Bearer $TOKEN" \
   -T application/json \
   -p trade.json \
   http://localhost:8080/api/v1/trades
# open http://localhost:3000 — watch the ADV087 + ADV088 panels live
```

**Observe:**

- `ab` reports 100 successful (201) responses with a `Requests per second` and a percentile table; throughput stays above the agreed SLO.
- The TICKET-ADV087 request-rate panel spikes during the run and the TICKET-ADV088 P95 latency panel stays under ~200ms.
- No DLQ events fire on the Kafka topics; if `ab` errors mid-run on duplicate `tradeRef`, switch to the k6 fallback which templates the ref per iteration.

---

## End-of-day checklist

- `@Cacheable` is active on `InstrumentService.findBySymbol`; a second call for the same symbol is visibly faster and emits no DB-hit log line.
- Caffeine cache config defines two named caches (`instruments`, `counterparties`) with different TTLs (5 min, 1 min) and `recordStats()` enabled.
- Four custom Micrometer meters are live on `/actuator/prometheus`: `trade_created_total` (Counter), `reconciliation_duration_seconds` (Timer with histogram), `recon_break_count` (Gauge), `trade_value_total` (DistributionSummary with histogram).
- Grafana dashboard has six panels: API request rate by URI, P50/P95/P99 latency, trade-create rate, recon-duration histogram (heatmap or percentile lines), break-count stat with thresholds, trades-by-status pie chart.
- Two Prometheus alert rules are loaded and visible at `http://localhost:9090/rules`: `TooManyReconBreaks` and `HighApiLatency`.
- `recon-audit-starter` Maven module builds, installs locally, and auto-configures inside the main `recon-service` via the Boot-3 imports file.
- `ReconConfig` JMX MBean is visible in JConsole under `reconx:type=ReconConfig` with readable/writable attributes and one operation.
- Apache Bench or k6 load test of 100 requests at concurrency 10 has been run, completed cleanly, and the run is visible as a spike on your Grafana panels with throughput and P95 numbers recorded.
- You can answer the debrief question: trace the path of a single byte from `Counter.increment()` in `TradeService` to a pixel on a Grafana panel, naming every hop.

**Day 7 picks up the frontend story:** semantic HTML5, CSS Grid, Server-Sent Events, ARIA accessibility. The metrics you built today reappear as a live operational view students wire to via SSE.
