# Day 3 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day3/README.md](../../TrainersGuide/day3/README.md)
> **Module:** Java Modules 3 & 4 — Functional + Testing

## What you'll build today

Today you turn yesterday's sealed `Trade` model into a working reconciliation
engine and then prove, with tests, that it does the right thing. The morning
is functional Java in anger: you will rewrite a naive nested-loop reconciler
as a streamed pipeline, group and summarise trades with `Collectors`, write
two custom Collectors of your own, and fan out work across counterparties with
`CompletableFuture`. The afternoon is the discipline that keeps that code
honest: red-green-refactor with JUnit 5, fakes and spies with Mockito,
parameterised tolerance tests, and finally a real Postgres in a container so
your integration tests run against the same database your production code
will hit. By 17:00 you should have a `mvn verify` that passes, a JaCoCo
report above 85% on the service packages, and a confident answer to the
question "how do you know it works?".

## Day at a glance

1. Standup and Day-2 holdover unblock (sealed `Trade` hierarchy compiles cleanly for everyone)
2. AM mini-lecture (Module 3): Collections, generics, Streams, `java.time`, live JShell examples
3. **Workshop 3A — Streams + Collectors + CompletableFuture** (TICKET-ADV033 – TICKET-ADV039)
4. Lunch (and check Docker Desktop is running on your laptop)
5. PM mini-lecture (Module 4): JDBC, JUnit 5, Mockito, TDD red-green-refactor demo
6. **Workshop 3B — TDD with JUnit 5 + Mockito** (TICKET-ADV040 – TICKET-ADV043)
7. Break
8. **Workshop 3C — Testcontainers + integration + coverage** (TICKET-ADV044 – TICKET-ADV047)
9. End-of-day debrief and Day 4 preview

## Exercises

Fifteen exercises across three workshop blocks. Each exercise has a goal, a
short list of acceptance criteria you can verify yourself, and three
progressive hints. Open Hint 1 first — it points you in a direction. Open
Hint 2 only if you are still stuck — it names the specific API or pattern
you need. Open Hint 3 only as a last resort — it describes the shape of the
answer without giving you the code. If you find yourself opening Hint 3
without trying Hint 1 and 2, slow down: the muscle you are building today is
the *thinking*, not the typing.

### Workshop 3A — Streams + Collectors + CompletableFuture

This is the hardest morning of the week so far. You are not just learning
new syntax — you are rewiring how you think about iteration. By the end of
this block you should be able to look at a `for` loop with a mutable
accumulator and immediately see the Collector that replaces it.

### TICKET-ADV033 — ReconciliationEngine using Streams

**Goal:** Build a `reconcile(internal, external)` method on `ReconciliationEngine` that pairs trades from two lists and returns a map of results keyed by trade reference.

**What**
- `backend/src/main/java/com/dbtraining/reconx/service/ReconciliationEngine.java` exposes `reconcile(List<Trade> internal, List<Trade> external)` returning a `Map<String, ReconResult>`, built by pre-indexing the external side via `Collectors.toMap` keyed on `tradeRef`.

**Why**
- The streamed shape with an indexed lookup is the contract every later ticket assumes — ADV037 fans it out across threads, ADV040 drives it via TDD, ADV045 runs it against a Testcontainers Postgres.

**Observe**
- A 10k-trade input completes in a couple of seconds on a laptop; passing `null` or an empty list returns an empty map instead of throwing `NullPointerException`.

**Done when:**
- The method accepts two `List<Trade>` arguments and returns a `Map<String, ReconResult>`.
- Lookups against the external side are constant-time, not linear-scan.
- The pipeline produces correct results on a 10k-trade input within a couple of seconds on your laptop.
- Empty or null input lists do not throw — they return an empty result map.

<details>
<summary>Hint 1 — gentle direction</summary>

Before you write a single line, sketch the join on the whiteboard. If you walk every internal trade and, for each one, walk every external trade looking for a match, what is the cost on 10,000 trades on each side? There is a much cheaper shape if you do a little work up front on one of the two collections.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Pre-build an index of the external side keyed by `tradeRef` before you touch the internal side. The standard library's terminal stream operation for "turn a stream into a `Map`" is the right tool. Then stream the internal side and do a single lookup per element.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Two stream pipelines, back to back. The first collects the external list into a `Map` keyed by trade reference; supply a merge function so duplicate refs do not throw. The second streams the internal list, maps each trade to a `ReconResult` via a private `match` helper (which receives the indexed external trade, or null), and collects to a `Map`. Use a `LinkedHashMap` supplier on the second collect if you want stable ordering.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Guard `internal` for null/empty and return `List.of()` immediately.
2. Stream `external` (coerce null to empty list) and `Collectors.toMap` into a `Map<String, TradeType>` keyed by `tradeRef().value()`, with `(a, b) -> a` as the merge function so duplicate refs do not throw.
3. `parallelStream()` the internal list and `map` each entry through a `matchOne` helper that takes the internal trade plus the externally indexed trade (or null).
4. In `matchOne`, branch on null external (return `ReconResult.breakResult(...)` with reason `MISSING_EXTERNAL`), otherwise compare price/quantity via the `ReconciliationRule` and return either `matched(ref)` or a `VALUE_MISMATCH` break.
5. Return the result list via `.toList()` — no mutable accumulators anywhere.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/ReconciliationEngine.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.ReconciliationRule;
import com.dbtraining.reconx.model.TradeType;
import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationEngine {

    @Timed(value = "reconciliation.duration", description = "Wall time of reconcile()",
           percentiles = {0.5, 0.95, 0.99}, histogram = true)
    public List<ReconResult> reconcile(List<TradeType> internal,
                                       List<TradeType> external,
                                       ReconciliationRule rule) {
        if (internal == null || internal.isEmpty()) return List.of();

        Map<String, TradeType> externalByRef = (external == null ? List.<TradeType>of() : external)
                .stream()
                .collect(Collectors.toMap(t -> t.tradeRef().value(), Function.identity(), (a, b) -> a));

        return internal.parallelStream()
                .map(in -> matchOne(in, externalByRef.get(in.tradeRef().value()), rule))
                .toList();
    }

    private ReconResult matchOne(TradeType internal, TradeType external, ReconciliationRule rule) {
        String ref = internal.tradeRef().value();
        if (external == null) {
            return ReconResult.breakResult(ref, "MISSING_EXTERNAL",
                    "No external trade found for " + ref);
        }
        BigDecimal[] iPair = priceQty(internal);
        BigDecimal[] ePair = priceQty(external);
        if (rule.matches(iPair[0], iPair[1], ePair[0], ePair[1])) {
            return ReconResult.matched(ref);
        }
        return ReconResult.breakResult(ref, "VALUE_MISMATCH",
                "internal=%s/%s external=%s/%s".formatted(iPair[0], iPair[1], ePair[0], ePair[1]));
    }

    private BigDecimal[] priceQty(TradeType t) {
        return switch (t) {
            case com.dbtraining.reconx.model.EquityTrade e     -> new BigDecimal[]{e.price(),  e.quantity()};
            case com.dbtraining.reconx.model.FXTrade fx        -> new BigDecimal[]{fx.fxRate(), fx.notionalCcy1()};
            case com.dbtraining.reconx.model.BondTrade b       -> new BigDecimal[]{b.couponRate(), b.faceValue()};
            case com.dbtraining.reconx.model.DerivativeTrade d -> new BigDecimal[]{d.strike(), d.quantity()};
        };
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV033 end-to-end**

Run the engine's unit test to confirm the streamed pipeline matches correctly.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineTest
```

**Observe:**

- `ReconciliationEngineTest` reports green — exact-match scenario passes.
- The streamed pipeline returns results without `LazyInitializationException` or NPEs on empty input.
- Console shows the test taking well under a second on a 10k-trade pair.

---

### TICKET-ADV034 — Trade analytics with Collectors

**Goal:** Compute, per counterparty, a one-pass summary of trade notionals (count, sum, min, max, average).

**What**
- `service/TradeAnalyticsService.notionalByCounterparty(List<? extends TradeType>)` returns `Map<Long, NotionalSummary>` (or `DoubleSummaryStatistics`) using `Collectors.groupingBy` with a single-pass downstream summariser.

**Why**
- This is the bucket-then-aggregate shape you reuse in ADV036 (P&L per instrument) and ADV038 (custom `ReconSummaryCollector`); getting the downstream collector wired correctly here saves rewriting both.

**Observe**
- `./mvnw -pl backend test -Dtest=TradeAnalyticsServiceTest` is green and `BigDecimal` totals match a hand-calculated sum with no precision drift.

**Done when:**
- The method accepts a `List<Trade>` and returns a `Map<String, ?>` keyed by counterparty id, where the value carries all five aggregates.
- The pipeline traverses the input list exactly once (no two-pass loop).
- A short JShell or test snippet shows you can ask for the average notional for a known counterparty and get the right number.

<details>
<summary>Hint 1 — gentle direction</summary>

You want to bucket the trades by counterparty, and for each bucket compute several statistics at once. Both of those steps have a named Collector in the standard library — and the second of them returns an object that already holds count, sum, min, max, and average.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Reach for `Collectors.groupingBy` for the bucketing and a downstream summarising collector for the aggregates. The summarising flavour for numeric values returns a single object with all five statistics. Be aware of what that collector does to `BigDecimal` precision — the talking point in the briefing matters here.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

A single `trades.stream().collect(...)` call. The outer collector groups by counterparty id; the downstream collector accepts a `ToDoubleFunction<Trade>` that computes `price * quantity`. The return type is `Map<String, DoubleSummaryStatistics>`. Note in your code (a one-line comment is enough) that this is dashboard-grade, not settlement-grade.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `TradeAnalyticsService` and a `NotionalSummary` record carrying `count` and `total` (`BigDecimal`).
2. Stream the trades and group by counterparty id via `Collectors.groupingBy`.
3. As the downstream collector, use `collectingAndThen(toList(), list -> new NotionalSummary(...))` so the finisher can sum the notional with `BigDecimal::add` from `BigDecimal.ZERO` — avoiding `summarizingDouble` keeps full precision.
4. Add a `counterpartyIdOf(TradeType t)` switch over the sealed hierarchy to extract the counterparty id regardless of subtype.
5. Run a quick JShell test: build a few `EquityTrade`s with known notionals and assert the summary count and total.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/TradeAnalyticsService.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.EquityTrade;
import com.dbtraining.reconx.model.TradeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TradeAnalyticsService {

    public Map<Long, NotionalSummary> notionalByCounterparty(List<? extends TradeType> trades) {
        return trades.stream().collect(Collectors.groupingBy(
                t -> counterpartyIdOf(t),
                Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> new NotionalSummary(
                                list.size(),
                                list.stream()
                                    .map(t -> t.notional().amount())
                                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                )));
    }

    private long counterpartyIdOf(TradeType t) {
        return switch (t) {
            case EquityTrade e                                 -> e.counterpartyId();
            case com.dbtraining.reconx.model.FXTrade fx        -> fx.counterpartyId();
            case com.dbtraining.reconx.model.BondTrade b       -> b.counterpartyId();
            case com.dbtraining.reconx.model.DerivativeTrade d -> d.counterpartyId();
        };
    }

    public record NotionalSummary(long count, BigDecimal total) {}
}
```

</details>

**▶ Run the project — verify TICKET-ADV034 end-to-end**

Unit-test the collector grouping to confirm per-counterparty aggregates.

```bash
./mvnw -pl backend test -Dtest=TradeAnalyticsServiceTest
```

**Observe:**

- Groups by counterparty produce the correct trade counts per bucket.
- `NotionalSummary` fields are immutable (record components) — no setter compiles.
- `BigDecimal` totals are exact — no precision loss compared to a hand-calculated sum.

---

### TICKET-ADV035 — VWAP via a custom Collector

**Goal:** Write a custom `Collector<Trade, ?, BigDecimal>` that computes the volume-weighted average price across a stream of trades.

**What**
- `service/VwapCollector` (or an inner class on `TradeAnalyticsService`) implements all four `Collector` contributions plus `Characteristics.UNORDERED`, producing `BigDecimal` VWAP to 4 decimal places with `RoundingMode.HALF_UP`.

**Why**
- Writing the four methods by hand once is the only way to understand why `combiner` must return a fresh accumulator — ADV038 reuses the same skeleton for `ReconSummaryCollector`, and ADV037 relies on the same parallel-safety reasoning.

**Observe**
- Running the same fixture through a serial `.stream()` and a `.parallelStream()` returns identical `BigDecimal` results; empty input returns `BigDecimal.ZERO` instead of an `ArithmeticException`.

**Done when:**
- The collector implements all four method contributions of `Collector` (supplier, accumulator, combiner, finisher) plus characteristics.
- The result is `BigDecimal` to 6 decimal places, rounded half-up, with no double precision loss along the way.
- A serial stream and a parallel stream over the same input produce identical results.
- An empty input returns `BigDecimal.ZERO` rather than throwing.

<details>
<summary>Hint 1 — gentle direction</summary>

VWAP is the sum of `price * quantity` divided by the sum of `quantity`. You need to carry two running totals through the reduction, not one. That is a hint about the *shape* of the accumulator object you supply.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Create a tiny inner accumulator class with two `BigDecimal` fields. Implement `Collector` directly — do not try to shoehorn this into `Collectors.of(...)` until you have done it the long way at least once. Your `combiner` must return a *new* accumulator that holds the sum of the two inputs' fields; returning either input mutated is a parallelism bug.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Four overrides plus characteristics. The supplier returns a new accumulator each call. The accumulator updates both fields in place. The combiner builds a new accumulator whose fields are the additions of the two inputs. The finisher guards against zero total quantity, then divides with explicit scale and rounding mode. Characteristics include `UNORDERED` only because VWAP is a commutative aggregate — do not add `CONCURRENT` or `IDENTITY_FINISH`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a public `vwapByInstrument(List<EquityTrade>)` method on `TradeAnalyticsService`.
2. Group trades by `EquityTrade::instrumentSymbol` into `Map<String, List<EquityTrade>>`.
3. Map each entry to its VWAP: sum quantities (`reduce(ZERO, BigDecimal::add)`), short-circuit to `BigDecimal.ZERO` if total is zero, else sum `price * qty` and divide with scale 4 and `RoundingMode.HALF_UP`.
4. Collect entries back into `Map<String, BigDecimal>` via `Collectors.toMap`.
5. Verify serial vs parallel stream over the same input produce identical totals — the reduce is associative.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/TradeAnalyticsService.java` — `vwapByInstrument` method):

```java
public Map<String, BigDecimal> vwapByInstrument(List<EquityTrade> equityTrades) {
    Map<String, List<EquityTrade>> bySymbol = equityTrades.stream()
            .collect(Collectors.groupingBy(EquityTrade::instrumentSymbol));

    return bySymbol.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> {
                BigDecimal totalQty = e.getValue().stream()
                        .map(EquityTrade::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalQty.signum() == 0) return BigDecimal.ZERO;
                BigDecimal weighted = e.getValue().stream()
                        .map(t -> t.price().multiply(t.quantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                return weighted.divide(totalQty, 4, RoundingMode.HALF_UP);
            }
    ));
}
```

</details>

**▶ Run the project — verify TICKET-ADV035 end-to-end**

Unit-test the custom VWAP collector on a known fixture.

```bash
./mvnw -pl backend test -Dtest=TradeAnalyticsServiceTest#vwap*
```

**Observe:**

- VWAP per instrument matches the hand-computed value to 4 decimal places.
- Serial-stream and parallel-stream invocations return identical `BigDecimal` results.
- Empty input returns `BigDecimal.ZERO` rather than throwing `ArithmeticException`.

---

### TICKET-ADV036 — P&L per instrument

**Goal:** Produce a `Map<String, BigDecimal>` of summed P&L per instrument symbol from a list of trades.

**What**
- `TradeAnalyticsService.pnlByInstrument(List<EquityTrade>)` returns `Map<String, BigDecimal>` using `groupingBy(EquityTrade::instrumentSymbol, mapping(this::pnl, reducing(BigDecimal.ZERO, BigDecimal::add)))`.

**Why**
- The three-arg `reducing` form with `BigDecimal.ZERO` identity is the parallel-safe pattern you carry into ADV037's `CompletableFuture` fan-out — a non-associative reducer here breaks parallelism there.

**Observe**
- Mixed BUY/SELL fixture returns hand-calculable totals and a parallel stream over the same input yields identical sums.

**Done when:**
- The map's keys are instrument symbols; values are `BigDecimal` sums.
- The pipeline is parallel-safe — the reduction uses an identity and an associative operator.
- Tests against a small fixture (three instruments, mixed signs) produce hand-calculable totals.

<details>
<summary>Hint 1 — gentle direction</summary>

Same bucketing pattern as TICKET-ADV034, but instead of summarising five statistics you want a single number per bucket. The standard library has a downstream collector for "reduce these values to one using an identity and a combiner", and it is the parallel-safe choice.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`groupingBy` keyed on the instrument symbol, with `reducing` as the downstream collector. The three-argument form of `reducing` takes the identity element, a mapper from the stream element to the value being reduced, and the associative binary operator. For `BigDecimal` addition, all three of those names are obvious.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

A one-liner `trades.stream().collect(groupingBy(<key>, reducing(<identity>, <mapper>, <op>)))`. The identity is `BigDecimal.ZERO`. The mapper extracts the P&L from a `Trade`. The operator is `BigDecimal::add`. No mutable state anywhere.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `pnlByInstrument(List<EquityTrade>)` to `TradeAnalyticsService`.
2. Group by `EquityTrade::instrumentSymbol` using `Collectors.groupingBy`.
3. Use `mapping(this::pnl, reducing(BigDecimal.ZERO, BigDecimal::add))` as the downstream — `mapping` projects each trade to its signed P&L, `reducing` folds it.
4. Implement the `pnl(EquityTrade t)` helper: compute `price * quantity`, negate when `side == BUY` (cost), keep positive when `SELL` (revenue).
5. Test against a small fixture with mixed BUY/SELL and assert hand-calculated totals.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/TradeAnalyticsService.java` — P&L methods):

```java
public Map<String, BigDecimal> pnlByInstrument(List<EquityTrade> equityTrades) {
    return equityTrades.stream().collect(Collectors.groupingBy(
            EquityTrade::instrumentSymbol,
            Collectors.mapping(this::pnl,
                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
    ));
}

private BigDecimal pnl(EquityTrade t) {
    BigDecimal abs = t.price().multiply(t.quantity());
    return t.side() == com.dbtraining.reconx.model.Side.SELL ? abs : abs.negate();
}
```

</details>

**▶ Run the project — verify TICKET-ADV036 end-to-end**

Unit-test the P&L-per-instrument reducer.

```bash
./mvnw -pl backend test -Dtest=TradeAnalyticsServiceTest#pnl*
```

**Observe:**

- Mixed BUY/SELL fixture produces the hand-calculable per-instrument totals.
- Map keys are instrument symbols; values are `BigDecimal` (no `Double` rounding).
- Parallel stream over the same input produces the same totals — `reducing` is associative.

---

### TICKET-ADV037 — CompletableFuture: parallel recon by counterparty

**Goal:** Split the reconciliation by counterparty so that each counterparty is reconciled on its own thread, then merge the results back into a single map.

**What**
- `ReconciliationEngine.reconcileByCounterparty(...)` groups both sides by counterparty id, fans out one `CompletableFuture.supplyAsync(..., executor)` per counterparty on an explicit bounded `ExecutorService`, and merges results via `allOf(...).thenApply(...)`.

**Why**
- Owning the executor (rather than leaning on the JVM common ForkJoinPool) is what makes ADV045's integration test deterministic and what Day 7's Kafka consumer thread-pool sizing builds on.

**Observe**
- Merged result size equals the sum of per-counterparty input sizes; the service exposes a `shutdown()` and `jstack` shows worker threads from your named pool, not `ForkJoinPool.commonPool-worker-*`.

**Done when:**
- Each counterparty's reconcile call runs on a thread you explicitly chose — not the JVM-wide common pool.
- The service waits for *all* per-counterparty futures before returning.
- Returning early or losing results is impossible — the merged map size equals the sum of the per-counterparty map sizes.
- The service exposes a `shutdown()` (or equivalent) for the executor it owns.

<details>
<summary>Hint 1 — gentle direction</summary>

What does "parallel by counterparty" actually mean? Group the trades on each side by counterparty, then run one reconciliation per group. The grouping is a Collector you already know; the parallelism is a question of *which thread runs each group's reconcile*.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `CompletableFuture.supplyAsync(..., executor)` with an `ExecutorService` you create and own. A bounded fixed pool sized to available processors is the right default. Combine the futures with `CompletableFuture.allOf(...)`, then `.thenApply` to harvest the results — do not call `.get()` inside a stream.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Three steps. Step one: group both internal and external trade lists by counterparty id into two maps. Step two: stream the entry set of the internal map, build one `CompletableFuture` per counterparty by calling `supplyAsync` with the engine's `reconcile`, collect to a list. Step three: `allOf(...)` over that list, `thenApply` joins each future and merges the per-counterparty maps with `HashMap::putAll`. Always pass the executor explicitly.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a `reconcileByCounterparty` method on `ReconciliationEngine` taking two `Map<Long, List<TradeType>>` (one per side) and a `ReconciliationRule`.
2. Stream the internal map's entry set; for each entry submit `CompletableFuture.supplyAsync` calling the single-counterparty `reconcile(...)` with the matching external batch (or `List.of()` if absent).
3. Collect those futures into a `List<CompletableFuture<List<ReconResult>>>`.
4. Wrap with `CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))` and `.thenApply` that joins each future and `flatMap`s the per-counterparty results into a single `List<ReconResult>`.
5. Return the composite future — caller blocks via `.join()` only at the boundary, never inside the stream.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/ReconciliationEngine.java` — `reconcileByCounterparty`):

```java
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
```

</details>

**▶ Run the project — verify TICKET-ADV037 end-to-end**

Run the engine test to confirm per-counterparty futures merge correctly.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineTest
```

**Observe:**

- Merged result size equals the sum of per-counterparty input sizes — nothing is lost.
- Each `CompletableFuture` runs on the explicit executor, not the JVM-wide common pool.
- No `LazyInitializationException` and no deadlock — `allOf().thenApply` releases the caller cleanly.

---

### TICKET-ADV038 — Custom Collector returning a ReconSummary

**Goal:** Write a Collector over a `Stream<ReconResult>` that returns a `ReconSummary` domain object carrying `total`, `matched`, and `broken` counts.

**What**
- `service/ReconSummaryCollector implements Collector<ReconResult, ReconSummary.Builder, ReconSummary>` with a mutable inner `Builder`, an accumulator that increments `total` plus one of `matched`/`broken`, and `Characteristics.UNORDERED` only (no `IDENTITY_FINISH`, no `CONCURRENT`).

**Why**
- This is the generalisation of ADV035's VWAP collector to a domain object and the data shape ADV047's all-mismatched edge-case test asserts against (`summary.matched() == 0`, `summary.broken() == 3`).

**Observe**
- A 10k-result parallel-stream collect returns the same `ReconSummary` values as the serial run, proving the combiner is associative.

**Done when:**
- A `ReconSummary` record (or class) exists with the three fields and a sensible `empty()` factory.
- The Collector implements all four contributions, with a `combiner` that adds counts from two partial summaries.
- A parallel-stream test over 10k results produces the same `ReconSummary` as the serial version.

<details>
<summary>Hint 1 — gentle direction</summary>

This is TICKET-ADV035 generalised: a domain object out instead of a `BigDecimal`. Same four-method skeleton, same combiner discipline. The difference is the accumulator type and the finisher's job.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Introduce an inner mutable `Builder` with three `long` fields. The accumulator increments `total` always and one of `matched` or `broken` based on the result status. The finisher constructs the immutable `ReconSummary` from the builder. Mark `UNORDERED` because count aggregates are order-independent.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`Collector<ReconResult, ReconSummary.Builder, ReconSummary>`. Supplier returns `new Builder()`. Accumulator mutates the builder. Combiner returns a *fresh* builder whose three counts are the field-wise sum of its inputs. Finisher reads the builder's fields into a new `ReconSummary` record. Characteristics: `UNORDERED` only.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create a `ReconSummary` record with `long total, matched, broken` and a static `empty()` factory returning zeros.
2. Inside it, add a static nested mutable `Builder` with the same three counters.
3. Create `ReconSummaryCollector implements Collector<ReconResult, ReconSummary.Builder, ReconSummary>`.
4. Supplier returns `Builder::new`; accumulator increments `total` and either `matched` or `broken` based on `r.status()`; combiner returns a fresh builder summing both inputs' counters; finisher constructs the immutable `ReconSummary`.
5. Return `Set.of(Characteristics.UNORDERED)` — do not add `IDENTITY_FINISH` (the finisher is non-trivial) or `CONCURRENT`.
6. Test serial and parallel streams over 10k results — assert identical `ReconSummary`.

**Reference solution** (from `TrainersGuide/day3/README.md`; no `backend/src/main` file exists yet — you create it):

`backend/src/main/java/com/dbtraining/reconx/service/ReconSummaryCollector.java`:

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.ReconResult;
import com.dbtraining.reconx.model.ReconResult.Status;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class ReconSummaryCollector
        implements Collector<ReconResult, ReconSummary.Builder, ReconSummary> {

    @Override public Supplier<ReconSummary.Builder> supplier() {
        return ReconSummary.Builder::new;
    }

    @Override public BiConsumer<ReconSummary.Builder, ReconResult> accumulator() {
        return (b, r) -> {
            b.total++;
            if (r.status() == Status.MATCHED) b.matched++;
            else b.broken++;
        };
    }

    @Override public BinaryOperator<ReconSummary.Builder> combiner() {
        return (a, b) -> {
            ReconSummary.Builder out = new ReconSummary.Builder();
            out.total   = a.total   + b.total;
            out.matched = a.matched + b.matched;
            out.broken  = a.broken  + b.broken;
            return out;
        };
    }

    @Override public Function<ReconSummary.Builder, ReconSummary> finisher() {
        return b -> new ReconSummary(b.total, b.matched, b.broken);
    }

    @Override public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
    }
}
```

`backend/src/main/java/com/dbtraining/reconx/service/ReconSummary.java`:

```java
package com.dbtraining.reconx.service;

public record ReconSummary(long total, long matched, long broken) {
    public static ReconSummary empty() { return new ReconSummary(0, 0, 0); }

    public static final class Builder {
        long total;
        long matched;
        long broken;
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV038 end-to-end**

Unit-test the custom `ReconSummaryCollector` against serial and parallel streams.

```bash
./mvnw -pl backend test -Dtest=ReconSummaryCollectorTest
```

**Observe:**

- A 10k-result parallel stream produces the same `ReconSummary` as the serial run — combiner is correct.
- `ReconSummary` record fields (`total`, `matched`, `broken`) are immutable after construction.
- `empty()` factory returns a summary with all counts at zero.

---

### TICKET-ADV039 — Optional chaining for null-safe lookups

**Goal:** Implement `counterpartyForTradeRef(String tradeRef)` that walks from a trade reference to its counterparty using `Optional` chaining, without any `if (x != null)` or `isPresent()` checks.

**What**
- `service/TradeLookupService.counterpartyForTradeRef(String)` is a single expression: `tradeRepo.findByRef(ref).map(Trade::counterpartyId).flatMap(cpRepo::findById).orElseThrow(...)`.

**Why**
- The `Optional.map`/`flatMap`/`orElseThrow` discipline established here is what Day 4's controllers reuse for 404 handling and what keeps service code free of nested null checks under Day 5's MockMvc tests.

**Observe**
- A `grep -E 'isPresent|\.get\(\)' src/main/java/com/dbtraining/reconx/service/TradeLookupService.java` returns zero hits and a missing trade ref throws `NoSuchElementException` with the ref in its message.

**Done when:**
- The method body is a single expression — repository lookup, two chained operations, then a terminal `orElseThrow`.
- No call to `Optional.get()` or `Optional.isPresent()` appears anywhere.
- The thrown exception carries the trade ref in its message.

<details>
<summary>Hint 1 — gentle direction</summary>

You have two lookups in sequence: trade by ref, then counterparty by id. The id only exists if the trade was found. Walking through that conditionally without `if`s is what `Optional`'s combinator methods exist for.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The first step turns a found trade into its counterparty id — that's a plain `map`. The second step turns a counterparty id into an `Optional<Counterparty>` (the repo's `findById` already returns Optional) — and chaining one Optional-returning function after another is `flatMap`, not `map`. Terminate with `orElseThrow` that supplies a `NoSuchElementException` carrying the trade ref.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

A single chain: `tradeRepo.findByRef(ref).map(<trade-to-cpId>).flatMap(cpRepo::findById).orElseThrow(<supplier>)`. No intermediate variables, no null checks, no `isPresent`. If a teammate adds `Optional.get()` defensively to "make sure", reject the change.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `TradeLookupService` with constructor-injected `TradeRepository` and `CounterpartyRepository`.
2. Implement `counterpartyForTradeRef(String tradeRef)` returning `Counterparty`.
3. Start the chain with `tradeRepo.findByTradeRef(tradeRef)` — an `Optional<Trade>`.
4. `.map(Trade::getCounterparty)` to walk through the entity association, or `.map(t -> t.getCounterparty().getId())` then `.flatMap(cpRepo::findById)` if you must round-trip through the id.
5. Terminate with `.orElseThrow(() -> new NoSuchElementException("No counterparty for tradeRef=" + tradeRef))`.
6. Verify by grepping the method body — zero `isPresent()`, zero `.get()`, single expression.

**Reference solution** (from `TrainersGuide/day3/README.md`; no `backend/src/main` file exists yet — you create it as `backend/src/main/java/com/dbtraining/reconx/service/TradeLookupService.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.Counterparty;
import com.dbtraining.reconx.model.Trade;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.TradeRepository;

import java.util.NoSuchElementException;

public class TradeLookupService {

    private final TradeRepository tradeRepo;
    private final CounterpartyRepository cpRepo;

    public TradeLookupService(TradeRepository tradeRepo, CounterpartyRepository cpRepo) {
        this.tradeRepo = tradeRepo;
        this.cpRepo = cpRepo;
    }

    public Counterparty counterpartyForTradeRef(String tradeRef) {
        return tradeRepo.findByRef(tradeRef)
                .map(Trade::counterpartyId)
                .flatMap(cpRepo::findById)
                .orElseThrow(() -> new NoSuchElementException(
                        "No counterparty resolvable for trade " + tradeRef));
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV039 end-to-end**

Unit-test the Optional chain on present and missing trades.

```bash
./mvnw -pl backend test -Dtest=TradeLookupServiceTest
```

**Observe:**

- A trade with a resolvable counterparty returns the expected `Counterparty`.
- A missing trade ref throws `NoSuchElementException` carrying the ref in its message.
- Source grep of the method body shows zero occurrences of `.isPresent()` or `.get()`.

---

### Workshop 3B — TDD with JUnit 5 + Mockito

**The rule for the next ninety minutes:** write the test first. If you find
yourself typing production code before there is a failing test pointing at
the gap, stop. Delete what you wrote. Open the test file. The point of TDD
is not to end up with tests — it is to end up with *design pressure* that
shapes the code you write. Skipping the red step skips the design.

### TICKET-ADV040 — Exact-match TDD cycle

**Goal:** Drive an exact-match test through red → green → refactor for the `reconcile` method.

**What**
- `backend/src/test/java/com/dbtraining/reconx/service/ReconciliationEngineTest.java` carries `testReconcile_exactMatch_returnsMatched` with `@DisplayName`, given/when/then comment blocks, and AssertJ `assertThat(out).hasSize(1)` then `.status()).isEqualTo(MATCHED)`.

**Why**
- Locking the red-green-refactor habit on the smallest possible scenario is what lets ADV041's parameterised tolerance test, ADV042's break-path test, and Day 5's MockMvc tests all build on a stable engine contract.

**Observe**
- The test is red against an empty engine, then green after the minimal implementation — surefire output shows the `@DisplayName` line and the assertion failure (when red) names actual vs expected status, not raw values.

**Done when:**
- A test method exists with a `@DisplayName` that reads like a sentence.
- The test follows a clear given/when/then structure (comments mark the three phases).
- The test fails first (you saw the red), then passes after you write the matching production behaviour.
- Asserts use AssertJ's fluent style, not raw `assertEquals`.

<details>
<summary>Hint 1 — gentle direction</summary>

Pick the smallest possible scenario you can imagine: one internal trade, one external trade, identical in every field. What status should the result carry? Now write the test that says exactly that — *before* you touch the engine.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `@Test` from JUnit Jupiter and `@DisplayName` to spell out the scenario in English. Use AssertJ's `assertThat(...).hasSize(1)` and `.isEqualTo(...)`. Construct two `Trade` records with identical fields and call `reconcile`. Run the test. It must be red — if it is green, you did not actually write a new scenario.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

One `@Test` method. Three blocks separated by `// given`, `// when`, `// then` comments. The given block constructs the two trades. The when block calls `engine.reconcile(List.of(internal), List.of(external))`. The then block asserts the map has size one and the single result has `Status.MATCHED`. Display name reads like "exact match on price and qty returns MATCHED".

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `ReconciliationEngineTest` with a field `engine = new ReconciliationEngine()` — no Spring needed.
2. Write the test method `testReconcile_exactMatch_returnsMatched` first; watch it red.
3. In the given block, build two `EquityTrade`s with identical `tradeRef`, price, quantity using a small `equity(...)` helper.
4. In the when block, call `engine.reconcile(List.of(internal), List.of(external), ReconciliationRule.EXACT)`.
5. Use AssertJ — `assertThat(out).hasSize(1)` then `assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.MATCHED)`.
6. Implement the engine, rerun, watch it green; refactor only after green.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/service/ReconciliationEngineTest.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV040 / TICKET-ADV041 / TICKET-ADV042 — TDD: write the test FIRST, then the impl.
 */
class ReconciliationEngineTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    @Test
    void testReconcile_exactMatch_returnsMatched() {
        EquityTrade internal = equity("EQU-20260603-0001", "100.00", "1000");
        EquityTrade external = equity("EQU-20260603-0001", "100.00", "1000");

        List<ReconResult> out = engine.reconcile(List.of(internal), List.of(external), ReconciliationRule.EXACT);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.MATCHED);
    }

    private EquityTrade equity(String ref, String price, String qty) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol("SAP.DE")
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .currency("EUR").side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV040 end-to-end**

Drive the exact-match TDD cycle through `./mvnw test`.

```bash
./mvnw -pl backend test
```

**Observe:**

- `testReconcile_exactMatch_returnsMatched` shows green in the surefire output.
- AssertJ failure messages, if any, name the actual vs expected status — not raw `assertEquals` output.
- The `@DisplayName` reads like an English sentence in the IDE/test report.

---

### TICKET-ADV041 — Tolerance test with @ParameterizedTest

**Goal:** Drive a parameterised test that runs the same matching assertion over multiple price differences within the tolerance threshold.

**What**
- A `@ParameterizedTest(name = "price diff {0} stays within 1% tolerance -> MATCHED")` method on `ReconciliationEngineTest` feeds `@ValueSource(strings = {"0.10", "0.50", "0.99"})` and parses each input via `new BigDecimal(diff)` — no `double` literals anywhere.

**Why**
- Driving tolerance with string-sourced `BigDecimal` is the precision discipline ADV035's VWAP collector and ADV046's coverage gate both rely on; a single `@ValueSource(doubles = ...)` silently loses precision and corrupts downstream assertions.

**Observe**
- Surefire prints one labelled row per value (`price diff 0.10 ...`, `0.50 ...`, `0.99 ...`), all green, and a `grep -F 'doubles' src/test` returns zero hits.

**Done when:**
- The test uses `@ParameterizedTest` with a value source providing the price differences.
- All differences in the source are within the tolerance — the test asserts MATCHED for every one of them.
- The display name template includes the parameter so test reports show one line per input value.
- No floating-point literals appear anywhere — values flow in as strings and parse to `BigDecimal`.

<details>
<summary>Hint 1 — gentle direction</summary>

The same scenario, with the same assertion, parameterised by one varying input — the price delta. Parameterised tests reduce *code surface*, not test surface; you still get one assertion run per value. Pick three values that all sit inside the tolerance.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Replace the `@Test` annotation with `@ParameterizedTest(name = "...")` and feed values via `@ValueSource(strings = {...})`. Parse each string into a `BigDecimal` inside the test body. Crucially, do *not* use `@ValueSource(doubles = ...)` — floating-point literals lose precision before the test even sees them.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Method signature accepts a `String diff` parameter. Display name template embeds `{0}` so each run is labelled. The given block reads `basePrice.add(new BigDecimal(diff))` for the external trade's price. The when and then blocks are identical to TICKET-ADV040. If you also want to test out-of-tolerance prices, write a *separate* parameterised test — never mix passing and failing inputs in the same source.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Annotate the method `@ParameterizedTest(name = "price diff {0} stays within 1% tolerance -> MATCHED")`.
2. Feed strings only — `@ValueSource(strings = {"0.10", "0.50", "0.99"})` — so `BigDecimal` is parsed exactly.
3. Take a `String diff` parameter and parse it with `new BigDecimal(diff)` inside the method.
4. Reuse the `equity(...)` helper to build the internal trade at base price 100.00 and the external at `basePrice.add(new BigDecimal(diff))`.
5. Call `engine.reconcile(..., ReconciliationRule.PRICE_TOLERANCE_1PCT)` and `assertThat(out.get(0).status()).isEqualTo(MATCHED)`.
6. Write the *failing* out-of-tolerance scenarios in a separate parameterised test if needed.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/service/ReconciliationEngineTest.java` — add to the existing test class). The trainer's shipped code is a plain `@Test`; if you want the parameterised flavour the ticket asks for, the trainer guide's `@ParameterizedTest`/`@ValueSource(strings = {...})` skeleton is the reference — never use `doubles` for prices.

```java
@Test
void testReconcile_priceTolerance_withinThreshold() {
    EquityTrade internal = equity("EQU-20260603-0002", "100.00", "1000");
    EquityTrade external = equity("EQU-20260603-0002", "100.50", "1000");

    List<ReconResult> out = engine.reconcile(List.of(internal), List.of(external),
            ReconciliationRule.PRICE_TOLERANCE_1PCT);

    assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.MATCHED);
}
```

</details>

**▶ Run the project — verify TICKET-ADV041 end-to-end**

Run the test suite and watch the parametrised rows expand in the surefire report.

```bash
./mvnw -pl backend test
```

**Observe:**

- Parametrised cases show one labelled row per value (`price diff 0.10 ...`, `0.50 ...`, `0.99 ...`).
- All rows are green — every diff inside tolerance returns `MATCHED`.
- No floating-point literals appear in either source or test output — values are `BigDecimal` parsed from strings.

---

### TICKET-ADV042 — Missing counterparty trade

**Goal:** Assert that an internal trade with no matching external trade produces a `BREAK` carrying the reason `MISSING_COUNTERPARTY_TRADE`.

**What**
- A `testReconcile_missingCounterpartyTrade_returnsBreak` test in `ReconciliationEngineTest` supplies one internal `EquityTrade` plus `List.of()` external and fires two distinct AssertJ assertions — `status()` equals `BREAK` and `discrepancyType()` equals `"MISSING_EXTERNAL"` by exact equality.

**Why**
- Pinning down both the status and the reason-string contract here is what ADV047's edge-case refactor relies on and what Day 4's break-handling endpoints will surface to the UI.

**Observe**
- The reason string is asserted with `.isEqualTo("MISSING_EXTERNAL")`, not `.contains(...)` — renaming the reason in production code immediately fails the test instead of silently passing.

**Done when:**
- A test method exists that supplies an internal trade and an empty external list.
- The assertion verifies *both* the status and the reason string of the resulting `ReconResult`.
- The reason string is asserted by exact equality, not regex or `contains`.

<details>
<summary>Hint 1 — gentle direction</summary>

What should the engine produce when the internal book contains a trade but the external feed has nothing matching? That is the break-path scenario. Write the test that pins down both *that* it breaks and *why*.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Reuse the given/when/then shape from TICKET-ADV040. The given block has one internal trade; pass `List.of()` for the external list. The then block has two assertions on the single `ReconResult` — one for `status()`, one for `reason()`. Asserting on the reason string (a stable enum-like contract) is the right call; asserting on `toString()` would not be.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Single `@Test` method, descriptive `@DisplayName`. One internal trade with a memorable ref like `TRD-MISSING`. Call `engine.reconcile(List.of(internal), List.of())`. Extract the result by ref from the returned map. Assert the status equals `Status.BREAK` and the reason equals `MISSING_COUNTERPARTY_TRADE`. Two assertions; not one combined check.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a `@Test` method `testReconcile_missingCounterpartyTrade_returnsBreak` to `ReconciliationEngineTest`.
2. Build a single internal `EquityTrade` with a memorable ref like `EQU-20260603-0003`.
3. Call `engine.reconcile(List.of(internal), List.of(), ReconciliationRule.EXACT)`.
4. Assert `out.get(0).status()` is `ReconResult.Status.BREAK`.
5. Assert `out.get(0).discrepancyType()` equals the exact reason string (`"MISSING_EXTERNAL"`) — two separate `assertThat` lines.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/service/ReconciliationEngineTest.java` — add to the existing test class):

```java
@Test
void testReconcile_missingCounterpartyTrade_returnsBreak() {
    EquityTrade internal = equity("EQU-20260603-0003", "100.00", "1000");

    List<ReconResult> out = engine.reconcile(List.of(internal), List.of(), ReconciliationRule.EXACT);

    assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.BREAK);
    assertThat(out.get(0).discrepancyType()).isEqualTo("MISSING_EXTERNAL");
}
```

</details>

**▶ Run the project — verify TICKET-ADV042 end-to-end**

Run the test suite to confirm the break-path scenario asserts both fields.

```bash
./mvnw -pl backend test
```

**Observe:**

- `testReconcile_missingCounterpartyTrade_returnsBreak` is green.
- Two distinct `assertThat` assertions fire — status equals `BREAK` and reason equals `MISSING_EXTERNAL` by exact equality.
- The given/when/then comment block is visible in the source; the test reads top-to-bottom as a scenario.

---

### TICKET-ADV043 — Mockito ArgumentCaptor

**Goal:** Use `ArgumentCaptor` to assert that the *content* of the `ReconResult` passed to the repository's `save` method is correct — not just that `save` was called.

**What**
- `backend/src/test/java/com/dbtraining/reconx/service/ReconciliationServiceTest.java` uses `mock(ReconResultRepository.class)`, calls `service.runRecon(...)`, then `verify(repo).save(captor.capture())` with `ArgumentCaptor<ReconResult>` and asserts on `captor.getValue().tradeRef()` and `.status()`.

**Why**
- Capturing the saved object — rather than relying on `verify(..., times(1))` — is the assertion shape Day 4's controller tests reuse for request payloads and Day 7's Kafka producer tests reuse for published events.

**Observe**
- The test fails (not silently passes) if `runRecon` mutates the `ReconResult` between engine and repo; surefire output names the wrong `tradeRef` or `status` field, not just a missed call count.

**Done when:**
- A service-layer test exists that mocks the `ReconResultRepository`.
- The test uses `ArgumentCaptor<ReconResult>` to capture what was passed to `save`.
- Assertions check at least two fields of the captured object (trade ref and status).
- The test does not use `verify(..., times(1))` as its only assertion — capturing the value is the point.

<details>
<summary>Hint 1 — gentle direction</summary>

`verify(times)` answers "was it called". A captor answers "with what". When the load-bearing question is "did we save the right thing", you want the second answer. Mockito makes both possible — pick the right tool for the load-bearing assertion.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Create a `mock(ReconResultRepository.class)` and pass it into the service under test. After acting, build an `ArgumentCaptor.forClass(ReconResult.class)`, hand it to `verify(repo).save(captor.capture())`, then read `captor.getValue()` and assert on its fields. This is a unit test — do not boot Spring.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Given block: mock the repo, construct the real engine, wire them through a `ReconciliationService` constructor, build one pair of matching trades. When block: call the service's run-recon method with single-item lists. Then block: declare `ArgumentCaptor<ReconResult> captor = ArgumentCaptor.forClass(ReconResult.class)`, call `verify(repo).save(captor.capture())`, then two `assertThat` lines on `captor.getValue().tradeRef()` and `captor.getValue().status()`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create a `ReconciliationService` that wraps the engine and persists each result via a `ReconResultRepository`.
2. In the test, `mock(ReconResultRepository.class)` and pass it (plus a real `ReconciliationEngine`) to the service.
3. Build a matched pair of `EquityTrade`s; call `service.runRecon(List.of(internal), List.of(external), ReconciliationRule.EXACT)`.
4. Create `ArgumentCaptor<ReconResult> captor = ArgumentCaptor.forClass(ReconResult.class)`.
5. `verify(repo).save(captor.capture())` to grab the actual saved object.
6. AssertJ on `captor.getValue().tradeRef()` and `.status()` — two field-level assertions, not just `times(1)`.

**Reference solution** (from `TrainersGuide/day3/README.md`; no trainer test file exists yet — you create it as `backend/src/test/java/com/dbtraining/reconx/service/ReconciliationServiceTest.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.ReconResult;
import com.dbtraining.reconx.model.Trade;
import com.dbtraining.reconx.repository.ReconResultRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReconciliationServiceTest {

    @Test
    void testReconcile_savesResultWithMatchedStatus() {
        // given
        ReconResultRepository repo = mock(ReconResultRepository.class);
        ReconciliationEngine engine = new ReconciliationEngine();
        ReconciliationService svc = new ReconciliationService(engine, repo);

        Trade i = new Trade("TRD-1", "CP-1", "SAP.DE",
                new BigDecimal("10"), new BigDecimal("100"), LocalDate.now());
        Trade e = new Trade("TRD-1", "CP-1", "SAP.DE",
                new BigDecimal("10"), new BigDecimal("100"), LocalDate.now());

        // when
        svc.runRecon(List.of(i), List.of(e));

        // then
        ArgumentCaptor<ReconResult> captor = ArgumentCaptor.forClass(ReconResult.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().tradeRef()).isEqualTo("TRD-1");
        assertThat(captor.getValue().status()).isEqualTo(ReconResult.Status.MATCHED);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV043 end-to-end**

Run the parallel reconciliation test and confirm fan-out timing beats sequential.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineTest#testParallel
```

**Observe:**

- Wall-clock completion time is materially less than the sequential equivalent for the same input.
- Mockito `ArgumentCaptor` reads the saved `ReconResult` — `tradeRef` and `status` assertions pass.
- A thread-pool exhaustion warning appears in the log if the pool is sized smaller than the work fan-out.

---

### Workshop 3C — Testcontainers + integration + coverage

The afternoon's payoff: real Postgres, real JDBC, real coverage report. If
Docker Desktop is not running on your laptop, fix that first — none of these
exercises will work without it. If Docker is broken on your machine,
pair with a teammate at a working laptop rather than spending an hour
debugging Docker.

### TICKET-ADV044 — Set up Testcontainers PostgreSQL

**Goal:** Stand up a Testcontainers-managed PostgreSQL container that the Spring Boot test context can talk to.

**What**
- A `ReconciliationIntegrationTest` annotated `@SpringBootTest` and `@Testcontainers` declares `static @Container PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")` and a `static @DynamicPropertySource` that wires `spring.datasource.url/username/password` from `postgres::getJdbcUrl` and friends.

**Why**
- Booting the same Postgres image (`postgres:16-alpine`) the production stack uses is what makes ADV045's end-to-end assertion meaningful and what Day 6's Liquibase migrations will run against in CI.

**Observe**
- `./mvnw -pl backend verify` runs the sanity test green; `docker ps` during the build shows `postgres:16-alpine` running and `docker ps` after the build is clean — the container is auto-removed.

**Done when:**
- The test class is annotated to activate Testcontainers lifecycle management.
- The Postgres container is declared as a class-level `static` field with the correct annotation.
- Spring's datasource URL, username, and password resolve to the running container's values at test-run time.
- A trivial sanity test (no assertions yet) passes — proving the container started and Spring wired up.

<details>
<summary>Hint 1 — gentle direction</summary>

You need three things to line up: the container must start, JUnit must know about it, and Spring must be told the JDBC URL at the right moment. Each of those is an annotation. Get the three annotations right and you do not need any imperative setup code.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Class-level `@SpringBootTest` plus `@Testcontainers`. A `static` field of type `PostgreSQLContainer<?>` annotated `@Container`. A `static` method annotated `@DynamicPropertySource` that registers `spring.datasource.url/username/password` from the container's getters. Skipping `static` on either the field or the method is the most common failure mode — the container will not be shared, or Spring will silently ignore the properties.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Test class carries both annotations at the top. The field is `static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(...).withUsername(...).withPassword(...);`. The dynamic property source method is `static void props(DynamicPropertyRegistry r)` and registers each property via a method reference like `postgres::getJdbcUrl`. Add the Testcontainers Postgres and JUnit-jupiter dependencies to `backend/pom.xml` with `<scope>test</scope>`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Verify `testcontainers-postgresql` and `testcontainers-junit-jupiter` are in `backend/pom.xml` with `<scope>test</scope>`.
2. Annotate a new integration test class with `@SpringBootTest` and `@Testcontainers`.
3. Declare a `static @Container PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")` — share one container across the class.
4. Add a `static @DynamicPropertySource` method that registers `spring.datasource.url/username/password` via method references on the container.
5. Write a `@Test void contextLoads() {}` (or a one-line repo `count()` call) — passing proves both the container started and Spring wired up.
6. Run `./mvnw -Dtest=ReconciliationIntegrationTest test` — first run pulls the image (slow), subsequent runs reuse the layer cache.

**Reference solution** (from `TrainersGuide/day3/README.md`; no trainer integration-test file exists yet — you create it as `backend/src/test/java/com/dbtraining/reconx/service/ReconciliationIntegrationTest.java`):

```java
package com.dbtraining.reconx.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ReconciliationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconx")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void containerIsRunning() {
        // sanity: if this passes, all your wiring is correct.
        // The real assertions live in TICKET-ADV045.
    }
}
```

Required dependencies in `backend/pom.xml`:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
```

</details>

**▶ Run the project — verify TICKET-ADV044 end-to-end**

Run the integration phase with Docker Desktop active.

```bash
./mvnw -pl backend verify
docker ps
```

**Observe:**

- A Postgres container starts during the test run (`docker ps` shows `postgres:16-alpine`).
- The `containerIsRunning` sanity test passes — Spring's datasource resolved against the container.
- The container is auto-removed at the end of the test class; `docker ps` after the build is clean.

---

### TICKET-ADV045 — Integration test: insert → recon → verify

**Goal:** Write an end-to-end test that inserts trades via repository beans, runs the reconciliation service, and verifies the persisted `ReconResult` rows.

**What**
- An `insertedTradesAreReconciledAndPersisted` test in the ADV044 integration class autowires the three repository beans plus `ReconciliationService`, saves a matching trade pair, runs `runRecon(internalRepo.findAll(), externalRepo.findAll(), EXACT)`, then asserts on `reconResultRepo.findAll()`.

**Why**
- Hitting real SQL (not an H2 stand-in) catches the `@Lob`/Postgres mismatch and CHAR-vs-VARCHAR gotchas the stack-bugs list warns about and gives Day 5's REST contract a database it can actually round-trip.

**Observe**
- `reconResultRepo.findAll()` returns exactly one row with `tradeRef` equal to the seeded ref and `status` equal to `MATCHED`; the test fails loudly (not silently) if Liquibase has not run.

**Done when:**
- The test inserts at least one matching pair of trades (one in the internal repo, one in the external repo).
- The reconciliation service is invoked against `findAll()` of both repositories.
- After the call, querying the `recon_results` table via the repo returns exactly the expected number of rows.
- Each persisted row's status and trade ref are asserted explicitly.

<details>
<summary>Hint 1 — gentle direction</summary>

Mocks prove your *code path*. A Testcontainers integration test proves your *SQL works*. Pick a scenario where every layer is exercised — repositories save data, service reads it, service writes results, repositories read the results back. The assertion is on the final database state.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Inject the three repository beans and the `ReconciliationService` into the test class. In the given block, build a matching pair of trades and `save()` them on each repository. In the when block, call `runRecon(internalRepo.findAll(), externalRepo.findAll())`. In the then block, call `findAll()` on the `ReconResultRepository` and assert on the returned list.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Test method named after the scenario. Construct the two trades with identical refs (`TRD-INT-1` works). Save each to the right repo. Run recon. Read all results. Assert the list has size one and the single element's `status()` is `MATCHED` and `tradeRef()` equals `TRD-INT-1`. Three assertions, one round-trip through the real database.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `@Autowired` injections for `InternalTradeRepository`, `ExternalTradeRepository`, `ReconResultRepository`, and `ReconciliationService` to the `@SpringBootTest` test class from TICKET-ADV044.
2. In the given block, build one matching `EquityTrade` pair sharing tradeRef `EQU-INT-1`, save each to the matching repo.
3. In the when block, call `service.runRecon(internalRepo.findAll(), externalRepo.findAll(), ReconciliationRule.EXACT)`.
4. In the then block, call `reconResultRepo.findAll()` and use AssertJ on size, `tradeRef()`, and `status()`.
5. Add `@Transactional` if you want each test to roll back automatically (otherwise truncate tables in `@AfterEach`).

**Reference solution** (from `TrainersGuide/day3/README.md`; add to the integration-test class from ADV044):

```java
@Test
void insertedTradesAreReconciledAndPersisted() {
    // given — two matching trades, one in each repo
    Trade internal = new Trade("TRD-INT-1", "CP-1", "SAP.DE",
            new BigDecimal("100"), new BigDecimal("245.50"), LocalDate.now());
    Trade external = new Trade("TRD-INT-1", "CP-1", "SAP.DE",
            new BigDecimal("100"), new BigDecimal("245.50"), LocalDate.now());

    internalTradeRepo.save(internal);
    externalTradeRepo.save(external);

    // when
    reconciliationService.runRecon(
            internalTradeRepo.findAll(),
            externalTradeRepo.findAll());

    // then — exactly one MATCHED row landed in recon_results
    List<ReconResult> persisted = reconResultRepo.findAll();
    assertThat(persisted).hasSize(1);
    assertThat(persisted.get(0).status()).isEqualTo(ReconResult.Status.MATCHED);
    assertThat(persisted.get(0).tradeRef()).isEqualTo("TRD-INT-1");
}
```

</details>

**▶ Run the project — verify TICKET-ADV045 end-to-end**

Run the integration test and confirm the entity ↔ domain round-trip survives the database.

```bash
./mvnw -pl backend verify
```

**Observe:**

- A matching pair of trades inserted via the repository round-trips back through the recon service.
- JSON serialisation/deserialisation of the persisted `ReconResult` preserves every field — no nulls after fetch.
- `reconResultRepo.findAll()` returns exactly one row with the right `tradeRef` and `MATCHED` status.

---

### TICKET-ADV046 — JaCoCo coverage report ≥ 85%

**Goal:** Wire JaCoCo into the Maven build so that `mvn verify` produces a coverage report and fails the build when service-package coverage drops below 85%.

**What**
- The `jacoco-maven-plugin` (0.8.11) in `backend/pom.xml` declares three executions — `prepare-agent` (default), `report` (verify), and `check` (verify) — with a `<rules>` block targeting `com.dbtraining.reconx.service.*` and `com.dbtraining.reconx.repository.*` at `LINE` `COVEREDRATIO` `0.85`.

**Why**
- The coverage gate is what protects every later day's refactor from quietly losing test coverage — Day 7's Kafka consumers, Day 8's JWT filters, and Day 9's CI workflow all extend this same `check` rule.

**Observe**
- `./mvnw -pl backend clean verify` is green, `backend/target/site/jacoco/index.html` opens with >= 85% line coverage on the included packages, and commenting out one service test re-runs to a `Coverage checks have not been met` build failure.

**Done when:**
- The JaCoCo Maven plugin is declared in `backend/pom.xml`.
- The build runs three goals — prepare-agent, report, and check — across the right phases.
- A coverage threshold rule targets the `service.*` and `repository.*` packages with a minimum line ratio of 0.85.
- After `./mvnw clean verify`, `target/site/jacoco/index.html` opens in a browser and shows the report.

<details>
<summary>Hint 1 — gentle direction</summary>

Coverage measurement happens in two halves: an agent that watches the JVM as tests run, and a report generator that reads the agent's output. The Maven plugin packages both. The third piece — a check that fails the build below a threshold — is a separate execution of the same plugin.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Add the `jacoco-maven-plugin` with three `<execution>` blocks: one binding `prepare-agent` to the default phase, one binding `report` to `verify`, one binding `check` to `verify`. The check execution carries a `<rules>` block with an `element` of `BUNDLE`, `includes` listing the packages, and a `<limit>` with counter `LINE`, value `COVEREDRATIO`, minimum `0.85`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

A single `<plugin>` block inside `<build><plugins>`. Three executions with ids `prepare-agent`, `report`, `check`. The check rule includes `com.dbtraining.reconx.service.*` and `com.dbtraining.reconx.repository.*`. Run `./mvnw clean verify`. If the build fails on coverage, look at the report HTML to see which class is dragging the number down — that is your next test, not a lower threshold.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `backend/pom.xml` and locate the `<build><plugins>` section.
2. Add the `jacoco-maven-plugin` block with three executions: `prepare-agent` (default phase), `report` (bound to `verify`), `check` (also `verify`).
3. In the `check` execution, declare a `<rules>` block: `<element>BUNDLE</element>`, `<includes>` listing `com.dbtraining.reconx.service.*` and `com.dbtraining.reconx.repository.*`, and a `<limit>` with counter `LINE`, value `COVEREDRATIO`, minimum `0.85`.
4. Run `./mvnw clean verify`.
5. Open `backend/target/site/jacoco/index.html` in a browser — confirm the line ratio is above 85% and no included package is red.

**Reference solution** (from `TrainersGuide/day3/README.md` — add inside `<build><plugins>` in `backend/pom.xml`; the shipped trainer pom currently has only `prepare-agent` + `report`, so this ticket is the one that adds the `check` rule):

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <includes>
                            <include>com.dbtraining.reconx.service.*</include>
                            <include>com.dbtraining.reconx.repository.*</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

</details>

**▶ Run the project — verify TICKET-ADV046 end-to-end**

Run a full verify and open the coverage report.

```bash
./mvnw -pl backend clean verify
open backend/target/site/jacoco/index.html
```

**Observe:**

- `backend/target/site/jacoco/index.html` opens with line coverage ≥ 85% on `service.*` and `repository.*`.
- The `check` execution succeeds — green build.
- Dropping a service test (e.g. comment one out) and re-running produces a build error from the JaCoCo gate.

---

### TICKET-ADV047 — Refactor for edge cases

**Goal:** Add tests for three edge cases of `reconcile` and refactor the production code so all three pass.

**What**
- Three new `@Test` methods on `ReconciliationEngineTest` cover empty-internal (returns `Map.of()`), single-internal-no-external (one `BREAK` with reason `MISSING_EXTERNAL`), and all-mismatched (three `BREAK`s, `ReconSummary` reports `matched == 0`, `broken == 3`); the engine carries null/empty guards and a `(a, b) -> a` merge function in its `toMap` call.

**Why**
- These edge cases are the boundary contract Day 4's REST layer, Day 7's Kafka retry handler, and Day 9's chaos-test scenarios all assume — the engine never throws `NullPointerException` or `IllegalStateException` on duplicate refs.

**Observe**
- `./mvnw -pl backend test -Dtest=ReconciliationEngineTest` is green across all three new tests; the JaCoCo report from ADV046 shows the guard lines covered, not red.

**Done when:**
- A test exists for an empty internal list — the engine returns an empty result map without throwing.
- A test exists for a single internal trade with no external feed — the result is a single `BREAK` with reason `MISSING_COUNTERPARTY_TRADE`.
- A test exists for all-mismatched trades — every result is a `BREAK`, and a `ReconSummary` over those results has `total == broken` and `matched == 0`.
- The production `reconcile` method handles `null` and empty inputs explicitly and never throws an NPE on the boundary.

<details>
<summary>Hint 1 — gentle direction</summary>

Edge cases are where production code dies at 02:00. Three scenarios stand out: nothing on one side, one thing on one side, everything wrong on both sides. Write the tests first — they are the spec for the refactor.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

In the engine, guard the start of `reconcile` against `null` or empty `internal` (return `Map.of()`); coerce `null` external into `List.of()` before indexing. When you build the external index with `toMap`, supply a merge function so duplicate refs in the input do not throw `IllegalStateException`. Use `LinkedHashMap` as the supplier for the result if you want stable ordering.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Three new tests in `ReconciliationEngineTest`, each named after its scenario. The empty-internal test asserts `results.isEmpty()`. The single-trade-no-external test asserts size one and `BREAK` with the right reason. The all-mismatched test builds three trades on each side with deliberately different prices, runs the engine, then collects the results through the `ReconSummaryCollector` from TICKET-ADV038 and asserts `summary.matched() == 0` and `summary.broken() == 3`. In the engine, add the null/empty guards and the merge function — nothing else.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add three new `@Test` methods to `ReconciliationEngineTest`, one per edge case.
2. Empty-internal test: `engine.reconcile(List.of(), List.of(...), EXACT)` — assert `isEmpty()`.
3. Single-trade-no-external test: one internal, `List.of()` external, assert size 1, `BREAK`, reason `MISSING_EXTERNAL`.
4. All-mismatched test: three internals at price 100, three externals at price 200 (different prices); reconcile, then `results.stream().collect(new ReconSummaryCollector())` and assert `matched() == 0`, `broken() == 3`, `total() == 3`.
5. In the engine, confirm the `internal == null || internal.isEmpty()` guard, the `external == null ? List.of() : external` coercion, and the `(a, b) -> a` merge function are all in place — nothing else.
6. Run `./mvnw -Dtest=ReconciliationEngineTest test` and confirm green.

**Reference solution** — the trainer ships **one** edge-case test in `ReconciliationEngineTest.java` (the empty-internal one) plus the production guards in `ReconciliationEngine.reconcile(...)`. The other two scenarios (single-trade-no-external and all-mismatched-summary) are spec-only — write them yourself following the shape below.

`backend/src/test/java/com/dbtraining/reconx/service/ReconciliationEngineTest.java` — verified trainer test:

```java
@Test
void testReconcile_emptyInternal_returnsEmpty() {
    assertThat(engine.reconcile(List.of(), List.of(), ReconciliationRule.EXACT)).isEmpty();
}
```

`backend/src/main/java/com/dbtraining/reconx/service/ReconciliationEngine.java` — the production guards (already in the shipped engine; verbatim from trainer source):

```java
public List<ReconResult> reconcile(List<TradeType> internal,
                                   List<TradeType> external,
                                   ReconciliationRule rule) {
    if (internal == null || internal.isEmpty()) return List.of();

    Map<String, TradeType> externalByRef = (external == null ? List.<TradeType>of() : external)
            .stream()
            .collect(Collectors.toMap(t -> t.tradeRef().value(), Function.identity(), (a, b) -> a));

    return internal.parallelStream()
            .map(in -> matchOne(in, externalByRef.get(in.tradeRef().value()), rule))
            .toList();
}
```

Spec-only tests to write yourself (no shipped trainer source — pattern follows ADV042 and ADV038):

```java
// single internal trade with no external feed -> one BREAK with MISSING_EXTERNAL
@Test
void testReconcile_singleInternalNoExternal_returnsBreak() {
    EquityTrade internal = equity("EQU-20260603-EDGE-1", "100.00", "1000");

    List<ReconResult> out = engine.reconcile(List.of(internal), List.of(), ReconciliationRule.EXACT);

    assertThat(out).hasSize(1);
    assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.BREAK);
    assertThat(out.get(0).discrepancyType()).isEqualTo("MISSING_EXTERNAL");
}

// all-mismatched -> ReconSummaryCollector reports total == broken, matched == 0
@Test
void testReconcile_allMismatched_summaryShowsZeroMatched() {
    List<TradeType> internals = List.of(
            equity("EQU-MM-1", "100.00", "1000"),
            equity("EQU-MM-2", "100.00", "1000"),
            equity("EQU-MM-3", "100.00", "1000"));
    List<TradeType> externals = List.of(
            equity("EQU-MM-1", "200.00", "1000"),
            equity("EQU-MM-2", "200.00", "1000"),
            equity("EQU-MM-3", "200.00", "1000"));

    List<ReconResult> out = engine.reconcile(internals, externals, ReconciliationRule.EXACT);
    ReconSummary summary = out.stream().collect(new ReconSummaryCollector());

    assertThat(summary.total()).isEqualTo(3);
    assertThat(summary.matched()).isEqualTo(0);
    assertThat(summary.broken()).isEqualTo(3);
}
```

</details>

**▶ Run the project — verify TICKET-ADV047 end-to-end**

Run the edge-case suite and confirm all three scenarios pass.

```bash
./mvnw -pl backend test -Dtest=ReconciliationEngineEdgeCasesTest
```

**Observe:**

- Empty-internal scenario returns an empty result list with no exception.
- All-mismatched scenario produces a 100% break ratio (`broken == 3`, `matched == 0`).
- Single-trade-no-external scenario is classified as `BREAK` with reason `MISSING_EXTERNAL` — never NPE.

---

## End-of-day checklist

By 17:00 you should be able to tick every item below. If two or more are
still open, flag it at the debrief — the morning of Day 4 is the right time
to close them before Spring layers more code on top.

- [ ] `ReconciliationEngine.reconcile(...)` runs as a streamed pipeline with an indexed external lookup, handles null/empty inputs, and uses a merge function for duplicate refs.
- [ ] `TradeAnalytics`, `VwapCollector`, `PnlCalculator` all compile and have at least one passing test each.
- [ ] `ParallelReconciliationService` runs on a bounded `ExecutorService` you created (not the common ForkJoinPool) and exposes a shutdown hook.
- [ ] `ReconSummaryCollector` has a correct combiner — verified by a parallel-stream test that compares against the serial result.
- [ ] `TradeLookupService.counterpartyForTradeRef` chains `map` and `flatMap` with no `isPresent` or `get` anywhere.
- [ ] `ReconciliationEngineTest` has at least one passing test per status (`MATCHED`, `BREAK` with each reason code).
- [ ] One Mockito test uses `ArgumentCaptor` to assert the content of a saved `ReconResult`, not just the fact of the call.
- [ ] A Testcontainers integration test starts Postgres, inserts trades, runs the recon, and verifies the persisted results.
- [ ] `./mvnw clean verify` passes locally and the JaCoCo report shows ≥ 85% line coverage on `service.*` and `repository.*`.
- [ ] Edge cases (empty list, single trade, all mismatched) are covered by explicit tests that pass.
