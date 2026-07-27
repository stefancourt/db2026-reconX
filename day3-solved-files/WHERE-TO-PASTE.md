# Day 3 — Solved Files & How To Run

Welcome to Day 3 — the streams-and-tests day. You'll fill out the
`ReconciliationEngine` (the spine of the whole product), a
`TradeAnalyticsService` full of Collectors, and real assertions for the
`ReconciliationEngineTest` that were sitting as `fail(...)` placeholders.

**How this folder works**

The real `backend/` tree ships with Day-3 methods as `TODO(TICKET-…)`
comments whose bodies do `throw new UnsupportedOperationException("…")`,
and the Day-3 tests sit at `fail("TICKET-… not implemented yet")`. This
folder contains **complete drop-in replacement files** — every TODO is
filled in, every `fail(...)` is a real assertion. You can either:

- **Overlay** the whole `backend/` subtree in one shot (fastest), or
- **Open each file** in this folder side-by-side with the starter to
  read the diff first, then copy the solved version over.

Both flows land at the same result.

**In this file you'll find:**

1. A one-line command to drop the solved files into your project.
2. A note about which Day-3 tickets ship as infrastructure/process only, not code.
3. A friendly walkthrough of what each file changed and why.
4. Step-by-step instructions to compile, run the Day-3 tests, and boot the app.
5. What success looks like and a quick troubleshooting cheat-sheet.

If you get stuck, read the log first — Spring and Maven are noisy but
almost always tell you exactly what's wrong.

---

## Quick start (TL;DR)

All paths below are relative to the **project root** (the directory that
contains `backend/`, `frontend/`, `db/`, `docker-compose.yml`).

The folder structure inside `day3-solved-files/` mirrors the project layout,
so you can copy the whole `backend/` tree over the top of the real
`backend/` tree and every file lands in the right place automatically.

```bash
# From the project root — one-shot overlay:
cp -R day3-solved-files/backend/ backend/
```

That command replaces exactly three files in `backend/` (the ones
listed in the "File-by-file map" below). Every other file in your
`backend/` tree is left alone.

Then jump to **"Run the project"** below.

---

## What's in scope here (and what isn't)

Day 3 has 15 tickets. Only three files actually needed code changes —
Everything else is either already done for you in the starter,
infrastructure (Docker + Testcontainers + JaCoCo plugin), or process
(PR review, ADR). Here's the honest breakdown:

| Ticket | Status | Where |
|--------|--------|-------|
| ADV018 helpers (exhaustive `switch` over sealed hierarchy) | ✓ in this folder | `ReconciliationEngine.priceQty`, `TradeAnalyticsService.counterpartyIdOf` |
| ADV033 — `ReconciliationEngine.reconcile()` with Streams | ✓ in this folder | `ReconciliationEngine.java` |
| ADV034 — notional-by-counterparty (Collectors) | ✓ in this folder | `TradeAnalyticsService.java` |
| ADV035 — VWAP per instrument | ✓ in this folder | `TradeAnalyticsService.java` |
| ADV036 — P&L per instrument | ✓ in this folder | `TradeAnalyticsService.java` |
| ADV037 — parallel recon by counterparty (CompletableFuture) | ✓ in this folder | `ReconciliationEngine.reconcileByCounterparty` |
| ADV038 — custom `Collector<ReconSummary>` | not shipped here | optional extension — the guide walks through it if you want extra practice |
| ADV039 — Optional chaining for null-safe lookups | not needed | starter already uses `Optional` where relevant |
| ADV040 — exact-match TDD test | ✓ in this folder | `ReconciliationEngineTest.testReconcile_exactMatch_returnsMatched` + `..._emptyInternal_returnsEmpty` |
| ADV041 — price-tolerance test | ✓ in this folder | `ReconciliationEngineTest.testReconcile_priceTolerance_withinThreshold` |
| ADV042 — missing-counterparty break test | ✓ in this folder | `ReconciliationEngineTest.testReconcile_missingCounterpartyTrade_returnsBreak` |
| ADV043 — Mockito `ArgumentCaptor` recipe | not shipped here | reference recipe in the guide; add if you introduce a mocked collaborator |
| ADV044 — Testcontainers Postgres | not shipped here | needs Docker running; follow the guide to add it locally |
| ADV045 — integration test insert → recon → verify | not shipped here | depends on ADV044 |
| ADV046 — JaCoCo coverage plugin | not shipped here | pom.xml plugin; opt-in when you're ready to enforce coverage |
| ADV047 — edge-case guards (null / empty / all-mismatched) | ✓ in this folder | wired into `reconcile()` and both analytics methods |

Everything with a ✓ compiles, runs, and passes tests on JDK 21.
Everything else is documented in the student guide and you can add it
when you want it — those tickets don't block the rest of Day 3.

---

## File-by-file map (what's in this folder)

| # | File in `day3-solved-files/` | Paste into (project-root path) | Tickets |
|---|------------------------------|--------------------------------|---------|
| 1 | `backend/src/main/java/com/dbtraining/reconx/service/ReconciliationEngine.java` | same path | ADV033 + ADV037 + ADV018 + ADV047 |
| 2 | `backend/src/main/java/com/dbtraining/reconx/service/TradeAnalyticsService.java` | same path | ADV034 + ADV035 + ADV036 + ADV018 + ADV047 |
| 3 | `backend/src/test/java/com/dbtraining/reconx/service/ReconciliationEngineTest.java` | same path | ADV040 + ADV041 + ADV042 |

---

## What each change does

### 1. `ReconciliationEngine.java` — ADV033 + ADV037 + ADV018 + ADV047

**`reconcile(internal, external, rule)`** — indexes `external` by
`tradeRef` into a `Map<String, TradeType>` (O(1) lookups vs O(n·m)
nested iteration), then `parallelStream`s over `internal` and calls
`matchOne(...)` for each. Null and empty inputs return `List.of()` up
front (that's the ADV047 edge-case guard).

**`reconcileByCounterparty(internalByCp, externalByCp, rule)`** — for
each counterparty key, spawns a `CompletableFuture.supplyAsync(() ->
reconcile(...))` and combines with `CompletableFuture.allOf(...)
.thenApply(v -> futures.flatMap(f.join()).toList())`. A missing
external feed for a counterparty is treated as an empty list, which
falls through to `MISSING_EXTERNAL` breaks — no crashes.

**`matchOne(internal, external, rule)`** — the per-row decision. Null
external → `BREAK` with `discrepancyType = "MISSING_EXTERNAL"`.
Otherwise pulls `priceQty()` for both sides and compares via
`rule.matches(...)`. On mismatch, produces a `VALUE_MISMATCH` break
whose `details` contains the four numbers so the recon analyst can see
exactly what diverged.

**`priceQty(TradeType t)`** — exhaustive `switch` over the four
permitted subtypes of the sealed `TradeType` (`EquityTrade`, `FXTrade`,
`BondTrade`, `DerivativeTrade`). Each case returns a
`BigDecimal[]{price, qty}` mapped to whatever "price" and "qty" mean
for that asset class (equity: price/quantity; FX: fxRate/notionalCcy1;
bond: couponRate/faceValue; derivative: strike/quantity). If someone
later adds a fifth trade type to `permits`, the compiler will fail this
switch — exactly the safety net a sealed hierarchy is designed to give
you.

### 2. `TradeAnalyticsService.java` — ADV034 + ADV035 + ADV036

**`notionalByCounterparty(trades)`** — `groupingBy(counterpartyIdOf,
collectingAndThen(toList(), list -> new NotionalSummary(size, sum of
notional.amount())))`. Empty / null input returns `Map.of()`.

**`vwapByInstrument(equityTrades)`** — `groupingBy(instrumentSymbol,
collectingAndThen(toList(), bucket -> sum(price·qty) / sum(qty)))`.
Uses `BigDecimal` throughout with `RoundingMode.HALF_UP` at 6 dp.
Divide-by-zero on empty qty short-circuits to `BigDecimal.ZERO` — no
`ArithmeticException` for illiquid instruments.

**`pnlByInstrument(equityTrades)`** — `groupingBy(instrumentSymbol,
mapping(pnl, reducing(ZERO, add)))`. `pnl(t)` computes
`abs = price * qty`, then `SELL → +abs`, `BUY → -abs`.

**`counterpartyIdOf(TradeType t)`** — the same sealed-hierarchy
exhaustive `switch` pattern as `priceQty`. Every leaf returns its own
`counterpartyId()`.

### 3. `ReconciliationEngineTest.java` — ADV040 + ADV041 + ADV042

Four real assertions replacing the four `fail(...)` placeholders:

- **`testReconcile_exactMatch_returnsMatched`** — two identical trades + `EXACT` rule → 1 result, `MATCHED`.
- **`testReconcile_priceTolerance_withinThreshold`** — 100.00 vs 100.50 + `PRICE_TOLERANCE_1PCT` → `MATCHED` (0.5% is inside 1%).
- **`testReconcile_missingCounterpartyTrade_returnsBreak`** — internal trade, no external → `BREAK` with `discrepancyType = "MISSING_EXTERNAL"`.
- **`testReconcile_emptyInternal_returnsEmpty`** — both sides empty → empty list (no NPE).

The `sampleEquity(ref, price, qty)` helper at the bottom of the file
was already in the starter — the tests just use it.

---

## Run the project

Day 3 is pure Java again — no Docker, no Postgres needed for the
in-scope tickets. You'll compile the code, run the tests, and boot the
whole app to make sure the reconciliation engine wires up without
regressions.

### Before you start — one-time checks

1. **Java 21.** Run `java -version`. If it says 21.x you're good. On macOS with multiple JDKs installed:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ```
2. **You're in the project root** — the folder that has `backend/`, `docker-compose.yml`, and `day3-solved-files/` side-by-side.
3. **You copied the solved files** — if not, do it now:
   ```bash
   cp -R day3-solved-files/backend/ backend/
   ```
4. **Days 1 and 2 are applied** — Day 3 code depends on the sealed `TradeType` hierarchy from Day 2 and the audit-log fix from Day 1. If you skipped either, overlay them too:
   ```bash
   cp -R day1-solved-files/backend/ backend/
   cp -R day2-solved-files/backend/ backend/
   ```

### Step 1 — Compile

```bash
cd backend
./mvnw -q clean compile
echo "exit=$?"
```

You want `exit=0`. A few `sun.misc.Unsafe` warnings from Maven's
internals are harmless — that's just modern Java shouting at Guice.

If you get real compile errors, jump to the troubleshooting section
before running tests.

### Step 2 — Run the Day-3 unit tests

```bash
./mvnw test -Dtest='ReconciliationEngineTest,ReconciliationRuleTest,EquityTradeTest'
```

You should see `BUILD SUCCESS` and something like `Tests run: 14, Failures: 0`.

The 14 = 4 recon-engine tests (Day 3) + 3 equity-trade tests (Day 2) +
7 parameterized reconciliation-rule rows (Day 2). Everything green
means your streams pipeline, your sealed-hierarchy switches, and your
edge-case guards all work.

### Step 3 — Boot the full app to prove nothing regressed

```bash
./mvnw spring-boot:run
```

Wait for the log line `Started ReconxApplication in X seconds` (about
4s on H2). In a second terminal:

```bash
curl http://localhost:8081/api/actuator/health
# → {"status":"UP","groups":["liveness","readiness"]}
```

If the app boots and health returns `UP`, then Spring wired the
`@Service`s, JPA validated the schema (Day 1 audit-log fix still
holding), and the `TradeType` sealed hierarchy (Day 2) plus the new
recon engine (Day 3) all coexist without conflict.

Hit `Ctrl+C` when you're done poking around.

---

## What success looks like

You're done with Day 3 when **all four** of these are true:

- `./mvnw clean compile` exits `0`.
- `./mvnw test -Dtest='ReconciliationEngineTest,ReconciliationRuleTest,EquityTradeTest'` prints `Tests run: 14, Failures: 0`.
- `./mvnw spring-boot:run` reaches `Started ReconxApplication`.
- `curl /api/actuator/health` returns `{"status":"UP"}`.

Bonus: hit `/actuator/prometheus` and search for
`reconciliation_duration_seconds` — that's the `@Timed` histogram from
ADV084 kicking in the moment you call `reconcile()`.

---

## If something goes wrong

**"Cannot find symbol EquityTrade / FXTrade / …"** — Day 2 isn't
applied. Overlay it:
```bash
cp -R day2-solved-files/backend/ backend/
```

**"Not all switch cases handled"** — someone added a new trade type to
`TradeType.permits` without updating the two exhaustive switches in
`ReconciliationEngine.priceQty` and `TradeAnalyticsService.counterpartyIdOf`.
Add a case for the new leaf.

**Tests fail with `TICKET-ADV040 not implemented yet`** — you didn't
copy the solved test file over. Re-run:
```bash
cp -R day3-solved-files/backend/ backend/
```

**Boot fails on `audit_log` schema validation** — you're missing the
Day-1 audit-log fix. Overlay Day 1:
```bash
cp -R day1-solved-files/backend/ backend/
```

**"Release version 21 not supported"** — wrong JDK. Fix with
`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` and retry.

**Port 8081 already in use** — an old app instance is still running:
```bash
lsof -i :8081        # note the PID
kill <PID>
```

**`java.util.ConcurrentModificationException` in `reconcile`** — you're
mutating a collection you're streaming over from a caller thread. The
solved engine returns a new list, so this only happens if you also
customised the caller. Copy the fresh `ReconciliationEngine.java` and
reboot.

Still stuck? Scroll the log up to the **first** `ERROR` line — that's
almost always the real cause. Send that one line to your trainer with
one sentence about what you tried and you'll be unstuck fast.

You made it — Day 3 is the hump. Get some rest.
