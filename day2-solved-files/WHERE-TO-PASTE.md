# Day 2 — Solved Files & How To Run

Nice work getting here — Day 2 is the big Java day. You built out the
`TradeType` sealed hierarchy, Builder pattern on four concrete trade
classes, `Money` and `TradeRef` value objects, a `TradeFactory`, and the
`ReconciliationRule` enum with per-constant behaviour. This folder has
the finished versions of every file you needed to touch.

**How this folder works**

The real `backend/` tree ships with Day-2 methods as `TODO(TICKET-…)`
comments whose bodies do `throw new UnsupportedOperationException("…")`,
and the Day-2 tests sit at `fail("TICKET-… not implemented yet")`. This
folder contains **complete drop-in replacement files** — every TODO is
filled in, every stubbed method has a real body, every `fail(...)` is
a real assertion. You can either:

- **Overlay** the whole `backend/` subtree in one shot (fastest), or
- **Open each file** in this folder side-by-side with the starter to
  read the diff first, then copy the solved version over.

Both flows land at the same result.

**In this file you'll find:**

1. A one-line command to drop the solved files into your project.
2. A note about which Day-2 tickets were already done for you in the starter (so you don't hunt for TODOs that don't exist).
3. A friendly walkthrough of what each file changed and why.
4. Step-by-step instructions to compile the code and run the tests that prove Day 2 works.
5. What success looks like and a quick troubleshooting cheat-sheet.

Take a breath, follow the steps in order, and read the log messages —
the JVM is chatty but almost always tells you exactly what's wrong.

---

## Quick start (TL;DR)

All paths below are relative to the **project root** (the directory that
contains `backend/`, `frontend/`, `db/`, `docker-compose.yml`).

The folder structure inside `day2-solved-files/` mirrors the project layout,
so you can copy the whole `backend/` tree over the top of the real
`backend/` tree and every file lands in the right place automatically.

```bash
# From the project root — one-shot overlay:
cp -R day2-solved-files/backend/ backend/
```

Then jump to **"Run the project"** below.

---

## What was already solved in the starter

Several Day-2 tickets were **already implemented** in the starter repo, so
this folder does not contain files for them. If you want to double-check,
grep for the TODO markers and confirm they are gone:

| Ticket | Status | Where |
|--------|--------|-------|
| ADV018 — sealed `TradeType` | ✓ done in starter | `backend/…/model/TradeType.java` |
| ADV025 — exception hierarchy (5 classes) | ✓ done in starter | `backend/…/exception/*.java` |
| ADV027 — `Comparable<TradeType>` natural ordering | ✓ done in starter | `backend/…/model/TradeType.java` |
| ADV029 — JSR-380 validation on the DTO | ✓ done in starter | `backend/…/dto/TradeRequest.java` |
| ADV031 — Javadoc on all public domain classes | ✓ done in starter | class-level WHAT/HOW/WHY on every model + exception class |
| ADV032 — PR review (non-code) | process ticket, see below | — |

---

## File-by-file map (what's in this folder)

| # | File in `day2-solved-files/` | Paste into (project-root path) | Tickets |
|---|------------------------------|--------------------------------|---------|
| 1 | `backend/src/main/java/com/dbtraining/reconx/model/Money.java`             | same path | ADV024 (plus/times bodies) |
| 2 | `backend/src/main/java/com/dbtraining/reconx/model/EquityTrade.java`      | same path | ADV019 + ADV028 + ADV030 |
| 3 | `backend/src/main/java/com/dbtraining/reconx/model/FXTrade.java`          | same path | ADV020 + ADV028 + ADV030 |
| 4 | `backend/src/main/java/com/dbtraining/reconx/model/BondTrade.java`        | same path | ADV021 + ADV028 + ADV030 |
| 5 | `backend/src/main/java/com/dbtraining/reconx/model/DerivativeTrade.java`  | same path | ADV022 + ADV028 + ADV030 |
| 6 | `backend/src/main/java/com/dbtraining/reconx/model/TradeFactory.java`     | same path | ADV023 |
| 7 | `backend/src/main/java/com/dbtraining/reconx/model/ReconciliationRule.java` | same path | ADV026 |
| 8 | `backend/src/test/java/com/dbtraining/reconx/model/EquityTradeTest.java`  | same path | ADV019 + ADV028 (test assertions) |
| 9 | `backend/src/test/java/com/dbtraining/reconx/model/ReconciliationRuleTest.java` | same path | ADV026 (parameterized assertions) |

---

## What each change does

### 1. `Money.java` — TICKET-ADV024

Filled in the two helper methods:

- `plus(Money other)` — validates same-currency, returns a new `Money` with summed amount. Throws `IllegalArgumentException` on currency mismatch.
- `times(BigDecimal multiplier)` — returns a new `Money` with `amount * multiplier`.

The compact-constructor validation (non-null, non-negative) and the static `of(...)` factories were already in the starter.

### 2. `EquityTrade.java` — TICKET-ADV019 (+ ADV028 + ADV030)

- `notional()` → `new Money(quantity.multiply(price), currency)`.
- `equals()` / `hashCode()` keyed on `tradeRef` only (natural key).
- `toString()` prints `ref, symbol, qty, price CCY, side` — deliberately **omits `counterpartyId`** (PII).
- `Builder.build()` — requireNonNull on every required field, positive-quantity/positive-price guard, then `new EquityTrade(this)`.

### 3. `FXTrade.java` — TICKET-ADV020 (+ ADV028 + ADV030)

- `notional()` → `new Money(notionalCcy1.multiply(fxRate), ccy2)` (converts into quote currency).
- `equals()`/`hashCode()` on `tradeRef`.
- `toString()` prints `ref, ccy1/ccy2, notional in ccy1, fxRate, side` — no `counterpartyId`.
- `Builder.build()` — requireNonNull, **`ccy1 != ccy2` invariant**, `fxRate > 0` guard.

### 4. `BondTrade.java` — TICKET-ADV021 (+ ADV028 + ADV030)

- `notional()` → `new Money(faceValue, currency)`.
- `equals()`/`hashCode()` on `tradeRef`.
- `toString()` prints `ref, isin, face CCY, coupon, maturity, side` — no `counterpartyId`.
- `Builder.build()` — requireNonNull, **`maturityDate >= tradeDate` invariant**.

### 5. `DerivativeTrade.java` — TICKET-ADV022 (+ ADV028 + ADV030)

- `notional()` → `new Money(strike.multiply(quantity), currency)` (simplified — real books use delta-adjusted).
- `equals()`/`hashCode()` on `tradeRef`.
- `toString()` prints `ref, optionType underlying on tradeDate, strike CCY, qty, expiry, side` — no `counterpartyId`.
- `Builder.build()` — requireNonNull, `strike > 0`, `quantity > 0`, **`expiry >= tradeDate` invariant**.

### 6. `TradeFactory.java` — TICKET-ADV023

- `create(assetClass, map)` — parses the asset-class string into the `TradeType.AssetClass` enum, then a switch expression dispatches to one of the four private builders. Switch is **exhaustive** — the compiler enforces that every sealed permit is handled.
- Four private builder helpers (`equity`, `fx`, `bond`, `derivative`) — each pulls typed values from the untyped `Map<String,Object>` payload and calls the concrete Builder.

Payload keys expected per asset class:

| assetClass | required keys |
|------------|---------------|
| `EQUITY`     | `tradeRef, symbol, quantity, price, currency, side, tradeDate, counterpartyId` |
| `FX`         | `tradeRef, ccy1, ccy2, notionalCcy1, fxRate, side, tradeDate, counterpartyId` |
| `BOND`       | `tradeRef, isin, faceValue, couponRate, maturityDate, currency, side, tradeDate, counterpartyId` |
| `DERIVATIVE` | `tradeRef, underlying, strike, quantity, expiry, optionType, currency, side, tradeDate, counterpartyId` |

### 7. `ReconciliationRule.java` — TICKET-ADV026

Filled in the `matches(...)` behaviour method. Returns `true` iff **both** the price diff (as a percentage of `internalPrice`, rounded to 6 dp) is within `priceTolerancePct` **and** the absolute quantity diff is within `qtyToleranceAbs`. Divide-by-zero on `internalPrice == 0` short-circuits to `priceDiffPct = 0`.

---

## TICKET-ADV032 — PR review (no code)

This is a **process ticket**, not a code ticket. Nothing to paste. What the ticket asks you to do:

1. Push your feature branch: `git push -u origin feature/day2-trade-model`.
2. Open a PR against the team integration branch (`gh pr create` or the GitHub UI).
3. Use a PR description with these sections:
   - **Scope** — one paragraph naming the tickets closed (ADV018 through ADV031).
   - **Files changed** — the 7 files in this folder plus a note that the "already-solved-in-starter" set was verified.
   - **Reviewer checklist** — sealed `permits` list correct, all leaves `final`, `BigDecimal` (not `double`) for money, `java.util.Currency` used, `equals`/`hashCode` on `tradeRef`, no PII in `toString`, JSR-380 only on the DTO, exceptions extend `ReconException`, `TradeFactory` has no static state.
   - **Smoke test** — `cd backend && ./mvnw test -Dtest=EquityTradeTest,TradeRefTest,MoneyTest`.
4. Request two reviewers. Reviewers tick boxes inline; any unticked box → a specific file:line request-changes comment.
5. Merge only after both approvals **and** a green smoke test on the merged branch.

---

## Run the project

Day 2 is pure Java — no Docker, no Postgres needed. You'll compile the
code, run the unit tests that prove your builders/factory/enum work,
and then boot the whole app to make sure nothing regressed.

### Before you start — one-time checks

1. **Java 21.** Run `java -version`. If it says 21.x you're good. On macOS with multiple JDKs installed:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ```
2. **You're in the project root** — the folder that has `backend/`, `docker-compose.yml`, and `day2-solved-files/` side-by-side.
3. **You copied the solved files** — if not, do it now:
   ```bash
   cp -R day2-solved-files/backend/ backend/
   ```
4. **Day 1 is applied** — if you skipped Day 1, apply it too (`cp -R day1-solved-files/backend/ backend/`). Day 2 code depends on the audit-log fix from Day 1 to boot the full app cleanly.

### Step 1 — Compile

```bash
cd backend
./mvnw -q clean compile
echo "exit=$?"
```

You want to see `exit=0`. A few Maven / `sun.misc.Unsafe` warnings are
harmless — that's just modern Java shouting at Maven's internals.

If you get real compile errors, jump to the troubleshooting section
before doing anything else — running tests on a broken tree wastes
minutes.

### Step 2 — Run the Day-2 unit tests

```bash
./mvnw test -Dtest='ReconciliationRuleTest,EquityTradeTest'
```

These are the two Day-2 test classes that ship with real assertions
(the guide lists more, but they're stubs the student can write later).
You should see `BUILD SUCCESS` and something like `Tests run: 10, Failures: 0`.

The `ReconciliationEngineTest` will still fail with
`TICKET-ADV040/041/042 not implemented yet` — that's **expected** and
**not your problem** on Day 2. Those are Day-3 tickets. Ignore.

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

If the app boots and health returns `UP`, your Day-2 domain classes are
being loaded successfully, the `TradeFactory` sees all four permitted
subtypes, and Hibernate is happy with the entity mappings. That's the
end-to-end confirmation.

Hit `Ctrl+C` when you're done.

---

## What success looks like

You're done with Day 2 when **all four** of these are true:

- `./mvnw clean compile` exits `0`.
- `./mvnw test -Dtest='ReconciliationRuleTest,EquityTradeTest'` says `Tests run: 10, Failures: 0`.
- `./mvnw spring-boot:run` reaches `Started ReconxApplication`.
- `curl /api/actuator/health` returns `{"status":"UP"}`.

Bonus if you want to feel really good: pop open one of the solved
`*Trade.java` files and eyeball the `equals`/`hashCode`/`toString`
methods. Notice that `toString` never prints `counterpartyId` — that's
the PII-safe logging from ADV030 doing its job.

---

## Quick spot-check for each ticket (optional)

If you want to verify a specific ticket by hand rather than running
tests, here's the one-liner behaviour to expect. You can paste these
into a `main(...)` scratch file or into the H2 console (for the ones
that hit the DB).

| Ticket | Try this                                              | Should…                                                |
|--------|-------------------------------------------------------|--------------------------------------------------------|
| ADV019 | `EquityTrade.builder().build()` with a missing field  | throw `NullPointerException`                           |
| ADV020 | Build an FXTrade where `ccy1 == ccy2`                 | throw `IllegalStateException`                          |
| ADV021 | Build a BondTrade with `maturityDate < tradeDate`     | throw `IllegalStateException`                          |
| ADV022 | Build a DerivativeTrade with `expiry < tradeDate`     | throw `IllegalStateException`                          |
| ADV023 | `TradeFactory.create("bogus", Map.of())`              | throw `IllegalArgumentException` from `valueOf`        |
| ADV024 | `Money.of("100","USD").plus(Money.of("50","EUR"))`    | throw `IllegalArgumentException`                       |
| ADV026 | `PRICE_TOLERANCE_1PCT.matches(100, 10, 100.5, 10)`    | return `true` — 0.5 % ≤ 1 %                            |
| ADV028 | Put two trades with the same `tradeRef` into a `Set`  | second insert is a no-op (Set stays size 1)            |
| ADV030 | Log an EquityTrade with `log.info("{}", trade)`       | see `ref=…, symbol=…`, and **no** `counterpartyId=`    |

---

## If something goes wrong

**"Cannot find symbol" on `TradeType.AssetClass`** — you didn't copy the solved files yet, or the copy didn't overwrite. Re-run:
```bash
cp -R day2-solved-files/backend/ backend/
```

**Compile error: "class not final"** — Java's sealed classes require every leaf (`EquityTrade`, `FXTrade`, `BondTrade`, `DerivativeTrade`) to be `final`. Check that you copied the whole `model/` folder, not just some of the files.

**Compile error: "release version 21 not supported"** — you're not on Java 21. `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` and retry.

**Tests fail with `TICKET-ADVxxx not implemented yet`** — those are placeholder `fail(...)` calls in the starter tests. The two classes that ship with real assertions are `ReconciliationRuleTest` and `EquityTradeTest` (both included above). Everything else in the guide's "Verify" blocks is a test the student is meant to write.

**Boot fails on `audit_log` schema validation** — you're missing the Day-1 audit-log fix. Apply Day 1 too:
```bash
cp -R day1-solved-files/backend/ backend/
```

**Port 8081 already in use** — an old copy of the app is still running:
```bash
lsof -i :8081        # note the PID
kill <PID>
```

Still stuck? Scroll up in the boot log to the **first** `ERROR` line
(not the last one — earlier errors cascade into later ones). That first
error is almost always the real cause. Show that line to your trainer
and you'll be unstuck in a minute.

You've got this — see you on Day 3.
