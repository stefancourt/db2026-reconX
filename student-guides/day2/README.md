# Day 2 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day2/README.md](../../TrainersGuide/day2/README.md)
> **Module:** Java Modules 1 & 2 — OOP Mastery + SOLID

## What you'll build today

Today you build the spine of the recon platform's model layer. By close-of-play you will have a sealed `TradeType` hierarchy with four concrete asset classes (equity, FX, bond, derivative), every one constructed via a Builder that enforces invariants at build time. You will add a `TradeFactory` for loosely-typed inbound payloads, value objects (`Money`, `TradeRef`), a four-level exception ladder, and a `ReconciliationRule` enum with embedded behaviour. You will finish by implementing contract methods (`Comparable`, `equals`, `hashCode`), JSR-380 validation on the inbound DTO, PII-safe `toString`, full Javadoc on the public surface, and a peer-reviewed PR. The deliverable is a model that makes invalid trades impossible to instantiate.

## Day at a glance

1. Standup and Day-1 holdover unblock
2. AM Module 1 lecture: OOP Mastery (4 pillars, sealed types, builders, abstract vs interface, exception hierarchy)
3. **Workshop 2A — Sealed hierarchy + builders** (TICKET-ADV018 – TICKET-ADV022)
4. Lunch
5. PM Module 2 lecture: Strings, I/O, SOLID applied to recon
6. **Workshop 2B — Factories, value objects, exceptions, enums** (TICKET-ADV023 – TICKET-ADV026)
7. **Workshop 2C — Contract methods, validation, docs** (TICKET-ADV027 – TICKET-ADV032)
8. End-of-day debrief and Day-3 preview

All 15 exercises land in `com.dbtraining.reconx.model` and `com.dbtraining.reconx.exception`. The exercises are tightly coupled — if your `TradeType` signature drifts from a teammate's `TradeFactory`, you will lose afternoon time to merge conflicts. Keep PRs small and review per sub-workshop.

## Exercises

There are 15 hands-on exercises, organised into three afternoon workshops. Each exercise lists acceptance criteria followed by three progressive hints. **Try Hint 1 first**, give yourself five minutes, then escalate to Hint 2 or Hint 3 only if you are still stuck. If Hint 3 has not unblocked you, raise your hand for the trainer — that is exactly what they are there for. Do not skim straight to Hint 3; the learning happens in the struggle between hints.

### Workshop 2A — Sealed hierarchy + builders

The foundation. If this workshop lands clean, the rest of the day follows. Take your time and get the shape right.

---

### TICKET-ADV018 — Sealed interface `TradeType` and abstract base

**Goal:** Establish the closed-world polymorphic root for every trade type in the platform, plus an internal shared-state base class.

**What**
- A `sealed interface TradeType permits EquityTrade, FXTrade, BondTrade, DerivativeTrade` in `com.dbtraining.reconx.model`, plus a package-private abstract base holding `tradeRef`, `notional`, `tradeDate` and validating them non-null in its constructor.

**Why**
- A sealed root closes the polymorphism world so the reconciliation switch in later days is exhaustive at compile time; this is the contract every leaf in ADV019-ADV022 has to satisfy.

**Observe**
- `./mvnw -pl backend compile` fails on the `permits` clause naming `EquityTrade`/`FXTrade`/`BondTrade`/`DerivativeTrade` as missing — the expected red until ADV019-ADV022 land.

**Done when:**
- A sealed interface `TradeType` exists in `com.dbtraining.reconx.model` and explicitly names the four permitted concrete trade types.
- An abstract base class holds the shared fields (trade reference, notional, trade date) and validates them as non-null on construction.
- The project compiles with no warnings; attempting to add an unrelated implementer outside the permits list fails compilation.

<details>
<summary>Hint 1 — gentle direction</summary>

Polymorphism without a closed world is what `abstract class` already gave you. Ask yourself what the new sealed-type feature is *adding* — and what discipline the compiler now requires of every implementer.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at the Java language guide on sealed types: every implementer of a sealed type must opt into exactly one of three modifiers. The interface is your *public* contract; the abstract base is your *internal* shared state — they can co-exist.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

You will have two files: a `sealed interface TradeType permits ...` exposing three accessor methods, and an `abstract sealed class Trade permits ...` (package-private) holding the three shared fields and validating them in its constructor. The four leaf classes will be `final` and extend the abstract base while implementing the interface.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/model/TradeType.java`.
2. Declare `public sealed interface TradeType permits EquityTrade, FXTrade, BondTrade, DerivativeTrade`.
3. Add four accessor signatures: `TradeRef tradeRef()`, `Money notional()`, `LocalDate tradeDate()`, `AssetClass assetClass()`.
4. Nest an `enum AssetClass { EQUITY, FX, BOND, DERIVATIVE }` on the interface as the discriminator.
5. Leave `Comparable` and the `NATURAL` comparator out for now — they land in TICKET-ADV027.
6. Run `./mvnw compile` — it will fail until the four permitted leaves exist, which is exactly what the next four tickets fix.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/TradeType.java`):

```java
package com.dbtraining.reconx.model;

import java.time.LocalDate;

public sealed interface TradeType
        permits EquityTrade, FXTrade, BondTrade, DerivativeTrade {

    TradeRef tradeRef();
    Money notional();
    LocalDate tradeDate();
    AssetClass assetClass();

    enum AssetClass { EQUITY, FX, BOND, DERIVATIVE }
}
```

</details>

**▶ Run the project — verify TICKET-ADV018 end-to-end**

After this ticket, the sealed `TradeType` interface and its `AssetClass` enum exist; the project does not yet compile cleanly because the four permitted leaves are not implemented (that lands in ADV019–ADV022).

```bash
# from project root
./mvnw -pl backend compile
```

**Observe:**

- `TradeType.java` is picked up by the compiler — no "cannot find symbol" on `TradeType` itself.
- Compilation fails on the `permits` clause with a message naming `EquityTrade` / `FXTrade` / `BondTrade` / `DerivativeTrade` as missing — that is the *expected* failure state until the next four tickets land.
- Adding a stub `class Rogue implements TradeType {}` outside the permits list produces a distinct compile error ("is not allowed in the sealed hierarchy").

---

### TICKET-ADV019 — `EquityTrade` with Builder pattern

**Goal:** Implement the first concrete trade with a fluent Builder that enforces required fields and domain invariants at build time.

**What**
- `model/EquityTrade.java`: a `final` class with private constructor, static `builder()`, inner `Builder` running `Objects.requireNonNull` and invariant checks inside `build()`, plus a `Side` enum (BUY/SELL) replacing any stringly-typed direction.

**Why**
- This is the template every other trade type (ADV020-ADV022) and the `TradeFactory` in ADV023 will copy; getting the build-then-validate shape right here saves rework three tickets later.

**Observe**
- `EquityTrade.builder().build()` with a missing `tradeRef` throws `NullPointerException("tradeRef")` from `requireNonNull` — not a downstream NPE inside the constructor body.

**Done when:**
- `EquityTrade` is `final`, has no public constructor, and is reachable only via a static `builder()` method.
- The Builder validates required fields *before* invoking the constructor; missing fields produce a clear, named error rather than a deep NullPointerException.
- A dedicated `Side` enum (BUY/SELL) replaces any stringly-typed direction field.

<details>
<summary>Hint 1 — gentle direction</summary>

Ask yourself when an error about a missing or invalid field should fire — at the moment the setter is called, or at the moment construction is finalised? The answer dictates where validation lives.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

There are two layers of defence in a well-built builder: a required-field gate (null checks with named messages) and an invariant gate (positivity, non-blank strings, etc.). The constructor itself should be private and receive an already-validated Builder.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Your `build()` method first runs null checks with named messages (one per required field), then runs invariant checks (quantity positive, price non-negative, symbol non-blank), then passes the Builder to a private constructor that copies the fields into final instance variables. The Side enum is a nested type on `EquityTrade`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `Side.java` first — a tiny top-level enum with `BUY, SELL`.
2. Create `EquityTrade.java` as `public final class EquityTrade implements TradeType` with eight `private final` fields and a `private` constructor that takes a `Builder`.
3. Add a `public static Builder builder()` entry point and a nested `public static final class Builder` with one setter per field returning `this`.
4. In `build()`: run `Objects.requireNonNull(...)` on every required field, then `signum() <= 0` checks on `quantity` and `price`, then return `new EquityTrade(this)`.
5. Implement the four `TradeType` accessors; `notional()` returns `new Money(quantity.multiply(price), currency)`.
6. Run `./mvnw test -Dtest=EquityTradeTest` once that test file lands later.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/Side.java`):

```java
package com.dbtraining.reconx.model;

public enum Side { BUY, SELL }
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/EquityTrade.java`):

```java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

public final class EquityTrade implements TradeType {

    private final TradeRef tradeRef;
    private final String instrumentSymbol;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final Currency currency;
    private final Side side;
    private final LocalDate tradeDate;
    private final long counterpartyId;

    private EquityTrade(Builder b) {
        this.tradeRef         = b.tradeRef;
        this.instrumentSymbol = b.instrumentSymbol;
        this.quantity         = b.quantity;
        this.price            = b.price;
        this.currency         = b.currency;
        this.side             = b.side;
        this.tradeDate        = b.tradeDate;
        this.counterpartyId   = b.counterpartyId;
    }

    public static Builder builder() { return new Builder(); }

    @Override public TradeRef tradeRef()    { return tradeRef; }
    @Override public LocalDate tradeDate()  { return tradeDate; }
    @Override public AssetClass assetClass(){ return AssetClass.EQUITY; }
    @Override public Money notional()       { return new Money(quantity.multiply(price), currency); }

    public String instrumentSymbol() { return instrumentSymbol; }
    public BigDecimal quantity()     { return quantity; }
    public BigDecimal price()        { return price; }
    public Currency currency()       { return currency; }
    public Side side()               { return side; }
    public long counterpartyId()     { return counterpartyId; }

    public static final class Builder {
        private TradeRef tradeRef;
        private String instrumentSymbol;
        private BigDecimal quantity;
        private BigDecimal price;
        private Currency currency;
        private Side side;
        private LocalDate tradeDate;
        private long counterpartyId;

        public Builder tradeRef(TradeRef v)           { this.tradeRef = v;        return this; }
        public Builder instrumentSymbol(String v)     { this.instrumentSymbol = v; return this; }
        public Builder quantity(BigDecimal v)         { this.quantity = v;        return this; }
        public Builder price(BigDecimal v)            { this.price = v;           return this; }
        public Builder currency(Currency v)           { this.currency = v;        return this; }
        public Builder currency(String code)          { return currency(Currency.getInstance(code)); }
        public Builder side(Side v)                   { this.side = v;            return this; }
        public Builder tradeDate(LocalDate v)         { this.tradeDate = v;       return this; }
        public Builder counterpartyId(long v)         { this.counterpartyId = v;  return this; }

        public EquityTrade build() {
            Objects.requireNonNull(tradeRef,         "tradeRef");
            Objects.requireNonNull(instrumentSymbol, "instrumentSymbol");
            Objects.requireNonNull(quantity,         "quantity");
            Objects.requireNonNull(price,            "price");
            Objects.requireNonNull(currency,         "currency");
            Objects.requireNonNull(side,             "side");
            Objects.requireNonNull(tradeDate,        "tradeDate");
            if (quantity.signum() <= 0) throw new IllegalStateException("quantity must be > 0");
            if (price.signum() <= 0)    throw new IllegalStateException("price must be > 0");
            return new EquityTrade(this);
        }
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV019 end-to-end**

After this ticket, `Side` and `EquityTrade` compile; an `EquityTrade` can only be constructed via the Builder, and missing required fields throw a named `NullPointerException` rather than a deep NPE inside the constructor.

```bash
# from project root
./mvnw -pl backend compile
./mvnw -pl backend test -Dtest=EquityTradeTest
```

**Observe:**

- `javap -p backend/target/classes/com/dbtraining/reconx/model/EquityTrade.class` shows the class is `final` and the only constructor is `private`.
- The unit test (`EquityTradeTest`) reports a happy-path build green and a "missing field" case throws `NullPointerException` with message `tradeRef` / `instrumentSymbol` etc.
- A negative `quantity` or `price` triggers `IllegalStateException("quantity must be > 0")` — caught before the object is ever returned.

---

### TICKET-ADV020 — `FXTrade` (two currencies and FX rate)

**Goal:** Build the FX trade type with two currency fields, a notional in the base currency, and an FX rate — all type-safe.

**What**
- `model/FXTrade.java` mirroring the `EquityTrade` shape but with `Currency ccy1`/`ccy2` (never `String`), `BigDecimal notionalCcy1`/`fxRate`, and Builder invariants rejecting equal currencies and non-positive `fxRate`.

**Why**
- The `Currency.getInstance(...)` boundary catches bad ISO codes like `"EURR"` immediately at the setter call rather than three layers downstream; the same pattern feeds the FX branch of `TradeFactory.fx(...)` in ADV023.

**Observe**
- `.ccy1("EURR")` throws `IllegalArgumentException` from `Currency.getInstance` at the setter call, before `build()` is ever reached.

**Done when:**
- Currency fields use `java.util.Currency`, never `String`.
- The Builder rejects equal `ccy1` and `ccy2`, and rejects non-positive `fxRate` or `notionalCcy1`.
- The notional currency convention is documented in Javadoc (notional is in `ccy1`).

<details>
<summary>Hint 1 — gentle direction</summary>

Currency codes are an external standard — there is already an ISO-4217 enforcement mechanism in the JDK. Lean on it. The cost is zero; the benefit is catching `"EURR"` at the boundary rather than three days later.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at `java.util.Currency.getInstance(String)` — it throws if the code is not ISO-4217. Then think about the FX-specific invariant that does not exist for an equity trade: two of your fields must not be equal.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`FXTrade` mirrors `EquityTrade` but with `Currency ccy1`, `Currency ccy2`, `BigDecimal notionalCcy1`, `BigDecimal fxRate`. The Builder's invariant section adds `ccy1.equals(ccy2)` rejection alongside the positivity checks. No `String` currency anywhere in the class.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Copy the `EquityTrade` skeleton and rename to `FXTrade`.
2. Swap the equity-specific fields for `Currency ccy1, Currency ccy2, BigDecimal notionalCcy1, BigDecimal fxRate`.
3. Builder setters for `ccy1`/`ccy2` take a `String` ISO code and call `Currency.getInstance(code)` — that throws `IllegalArgumentException` on a bad code, fail-fast at the boundary.
4. In `build()`, after the null checks, add the FX-specific invariants: `ccy1.equals(ccy2)` rejection and `fxRate.signum() <= 0` rejection.
5. Implement `notional()` as `new Money(notionalCcy1.multiply(fxRate), ccy2)` — notional rolls up in the quote currency.
6. `assetClass()` returns `AssetClass.FX`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/FXTrade.java`):

```java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

public final class FXTrade implements TradeType {

    private final TradeRef tradeRef;
    private final Currency ccy1;
    private final Currency ccy2;
    private final BigDecimal notionalCcy1;
    private final BigDecimal fxRate;
    private final Side side;
    private final LocalDate tradeDate;
    private final long counterpartyId;

    private FXTrade(Builder b) {
        this.tradeRef       = b.tradeRef;
        this.ccy1           = b.ccy1;
        this.ccy2           = b.ccy2;
        this.notionalCcy1   = b.notionalCcy1;
        this.fxRate         = b.fxRate;
        this.side           = b.side;
        this.tradeDate      = b.tradeDate;
        this.counterpartyId = b.counterpartyId;
    }

    public static Builder builder() { return new Builder(); }

    @Override public TradeRef tradeRef()     { return tradeRef; }
    @Override public LocalDate tradeDate()   { return tradeDate; }
    @Override public AssetClass assetClass() { return AssetClass.FX; }
    @Override public Money notional()        { return new Money(notionalCcy1.multiply(fxRate), ccy2); }

    public Currency ccy1()           { return ccy1; }
    public Currency ccy2()           { return ccy2; }
    public BigDecimal notionalCcy1() { return notionalCcy1; }
    public BigDecimal fxRate()       { return fxRate; }
    public Side side()               { return side; }
    public long counterpartyId()     { return counterpartyId; }

    public static final class Builder {
        private TradeRef tradeRef;
        private Currency ccy1, ccy2;
        private BigDecimal notionalCcy1, fxRate;
        private Side side;
        private LocalDate tradeDate;
        private long counterpartyId;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder ccy1(String code)           { this.ccy1 = Currency.getInstance(code); return this; }
        public Builder ccy2(String code)           { this.ccy2 = Currency.getInstance(code); return this; }
        public Builder notionalCcy1(BigDecimal v)  { this.notionalCcy1 = v; return this; }
        public Builder fxRate(BigDecimal v)        { this.fxRate = v; return this; }
        public Builder side(Side v)                { this.side = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder counterpartyId(long v)      { this.counterpartyId = v; return this; }

        public FXTrade build() {
            Objects.requireNonNull(tradeRef,     "tradeRef");
            Objects.requireNonNull(ccy1,         "ccy1");
            Objects.requireNonNull(ccy2,         "ccy2");
            Objects.requireNonNull(notionalCcy1, "notionalCcy1");
            Objects.requireNonNull(fxRate,       "fxRate");
            Objects.requireNonNull(side,         "side");
            Objects.requireNonNull(tradeDate,    "tradeDate");
            if (ccy1.equals(ccy2)) throw new IllegalStateException("ccy1 and ccy2 must differ");
            if (fxRate.signum() <= 0) throw new IllegalStateException("fxRate must be > 0");
            return new FXTrade(this);
        }
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV020 end-to-end**

After this ticket, `FXTrade` compiles, builder rejects equal `ccy1`/`ccy2`, bad ISO codes throw at the boundary (not three layers later), and `notional()` rolls up in the quote currency.

```bash
# from project root
./mvnw -pl backend test -Dtest=FXTradeTest
```

**Observe:**

- A happy-path build with `ccy1="EUR"`, `ccy2="USD"` succeeds and `notional().currency()` is `USD`.
- Builder call `.ccy1("EURR")` throws `IllegalArgumentException` from `Currency.getInstance(...)` immediately — not at `build()`.
- `ccy1=ccy2` (both `"EUR"`) makes `build()` throw `IllegalStateException("ccy1 and ccy2 must differ")`.
- No `String` currency field appears anywhere in `FXTrade` — only `java.util.Currency`.

---

### TICKET-ADV021 — `BondTrade` (coupon, maturity, face value, ISIN)

**Goal:** Build the bond trade type with fixed-income-specific fields and fail-fast validation on maturity ordering and ISIN shape.

**What**
- `model/BondTrade.java` with `BigDecimal couponRate`/`faceValue` (no `double` or `float` anywhere), a Builder that rejects `maturityDate` not strictly after `tradeDate`, and a length-12 ISIN shape check.

**Why**
- Domain invariants like "matures-before-traded is nonsense" belong in the constructor — this is the same fail-fast philosophy ADV019 introduced, applied to fixed income, and pre-empts a class of recon mismatches before Day 3.

**Observe**
- A `maturityDate` equal to or before `tradeDate` throws `IllegalStateException` with a message naming the maturity-ordering rule; `grep -E "double|float" model/BondTrade.java` returns no hits.

**Done when:**
- `couponRate`, `faceValue`, and the rest of the numeric fields are `BigDecimal` — no `double` or `float` anywhere in the class.
- The Builder rejects a `maturityDate` that is not strictly after `tradeDate`.
- ISIN shape is checked (length 12) in the Builder, even if richer ISIN validation will come later via JSR-380.

<details>
<summary>Hint 1 — gentle direction</summary>

A bond that matures before it is traded is nonsense — that is a domain invariant the constructor must refuse. Where do invariants live in this codebase? (You answered that in TICKET-ADV019.)

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at `LocalDate.isAfter(LocalDate)` for the date ordering check. For ISIN shape, a simple length check belongs in the Builder; pattern-matching detail can wait for JSR-380 in TICKET-ADV029.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`BondTrade` adds `BigDecimal couponRate`, `LocalDate maturityDate`, `BigDecimal faceValue`, `String isin`. Invariant block rejects: negative coupon, non-positive face value, maturity not strictly after trade date, ISIN length not equal to 12. Same two-layer build pattern as TICKET-ADV019.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Copy the `EquityTrade` skeleton and rename to `BondTrade`.
2. Add fields: `String isin, BigDecimal faceValue, BigDecimal couponRate, LocalDate maturityDate, Currency currency`.
3. In `build()`, after the null checks, add `if (maturityDate.isBefore(tradeDate)) throw new IllegalStateException("maturityDate cannot be before tradeDate")`.
4. `notional()` returns `new Money(faceValue, currency)` — bonds are valued at face.
5. `assetClass()` returns `AssetClass.BOND`.
6. Tighter ISIN regex validation is left for the JSR-380 layer in TICKET-ADV029.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/BondTrade.java`):

```java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

public final class BondTrade implements TradeType {

    private final TradeRef tradeRef;
    private final String isin;
    private final BigDecimal faceValue;
    private final BigDecimal couponRate;
    private final LocalDate maturityDate;
    private final Currency currency;
    private final Side side;
    private final LocalDate tradeDate;
    private final long counterpartyId;

    private BondTrade(Builder b) {
        this.tradeRef       = b.tradeRef;
        this.isin           = b.isin;
        this.faceValue      = b.faceValue;
        this.couponRate     = b.couponRate;
        this.maturityDate   = b.maturityDate;
        this.currency       = b.currency;
        this.side           = b.side;
        this.tradeDate      = b.tradeDate;
        this.counterpartyId = b.counterpartyId;
    }

    public static Builder builder() { return new Builder(); }

    @Override public TradeRef tradeRef()     { return tradeRef; }
    @Override public LocalDate tradeDate()   { return tradeDate; }
    @Override public AssetClass assetClass() { return AssetClass.BOND; }
    @Override public Money notional()        { return new Money(faceValue, currency); }

    public String isin()              { return isin; }
    public BigDecimal faceValue()     { return faceValue; }
    public BigDecimal couponRate()    { return couponRate; }
    public LocalDate maturityDate()   { return maturityDate; }
    public Currency currency()        { return currency; }
    public Side side()                { return side; }
    public long counterpartyId()      { return counterpartyId; }

    public static final class Builder {
        private TradeRef tradeRef;
        private String isin;
        private BigDecimal faceValue, couponRate;
        private LocalDate maturityDate, tradeDate;
        private Currency currency;
        private Side side;
        private long counterpartyId;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder isin(String v)              { this.isin = v; return this; }
        public Builder faceValue(BigDecimal v)     { this.faceValue = v; return this; }
        public Builder couponRate(BigDecimal v)    { this.couponRate = v; return this; }
        public Builder maturityDate(LocalDate v)   { this.maturityDate = v; return this; }
        public Builder currency(String code)       { this.currency = Currency.getInstance(code); return this; }
        public Builder side(Side v)                { this.side = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder counterpartyId(long v)      { this.counterpartyId = v; return this; }

        public BondTrade build() {
            Objects.requireNonNull(tradeRef,     "tradeRef");
            Objects.requireNonNull(isin,         "isin");
            Objects.requireNonNull(faceValue,    "faceValue");
            Objects.requireNonNull(couponRate,   "couponRate");
            Objects.requireNonNull(maturityDate, "maturityDate");
            Objects.requireNonNull(currency,     "currency");
            Objects.requireNonNull(side,         "side");
            Objects.requireNonNull(tradeDate,    "tradeDate");
            if (maturityDate.isBefore(tradeDate))
                throw new IllegalStateException("maturityDate cannot be before tradeDate");
            return new BondTrade(this);
        }
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV021 end-to-end**

After this ticket, `BondTrade` compiles, the builder refuses a maturity before the trade date, and every numeric field is `BigDecimal` — no `double`/`float` leaks through.

```bash
# from project root
./mvnw -pl backend test -Dtest=BondTradeTest
```

**Observe:**

- A happy-path build with `maturityDate` strictly after `tradeDate` succeeds and `notional()` returns `Money(faceValue, currency)`.
- Setting `maturityDate` before `tradeDate` makes `build()` throw `IllegalStateException("maturityDate cannot be before tradeDate")`.
- `grep -E "\\b(double|float)\\b" backend/src/main/java/com/dbtraining/reconx/model/BondTrade.java` returns nothing.
- ISIN length other than 12 is caught (either in the builder now or deferred to JSR-380 in ADV029, per your choice — document which).

---

### TICKET-ADV022 — `DerivativeTrade` (underlying, strike, expiry, option type)

**Goal:** Build the derivative trade type with an option-type enum and strike/expiry validation, without over-validating historical records.

**What**
- `model/DerivativeTrade.java` with a nested `enum OptionType { CALL, PUT }`, positive-`BigDecimal` strike, and a Builder check that `expiry` is strictly after `tradeDate` — but NO check against `LocalDate.now()`.

**Why**
- A historical option that has already expired is a valid record for replay and reconciliation; comparing `expiry` to `LocalDate.now()` would brick yesterday's data — the distinction between invariant and runtime condition matters here and in ADV026's tolerance rules.

**Observe**
- A build with `expiry` already in the past (relative to today) but after `tradeDate` succeeds; an `expiry` before `tradeDate` throws `IllegalStateException("expiry cannot be before tradeDate")`.

**Done when:**
- `OptionType` exists as an enum (CALL/PUT) nested on `DerivativeTrade`.
- `strike` is a positive `BigDecimal`; `expiry` is strictly after `tradeDate`.
- The class does **not** reject expired derivatives — an expired trade is a valid historical record. Document this decision.

<details>
<summary>Hint 1 — gentle direction</summary>

Think about the difference between an invariant (always true for any valid instance) and a runtime condition (true today, false tomorrow). Which category does "the expiry has not yet passed" fall into for a historical trade record?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Option type is a closed set of values — that is what enums are for. Use a nested `OptionType { CALL, PUT }` enum, not a `String`. For the expiry check, compare against `tradeDate`, not against `LocalDate.now()`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`DerivativeTrade` mirrors the others, with `String underlying`, `BigDecimal strike`, `LocalDate expiry`, `OptionType optionType`. The Builder rejects non-positive strike, blank underlying, and `expiry` not strictly after `tradeDate`. No check against the current date.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Copy the `EquityTrade` skeleton and rename to `DerivativeTrade`.
2. Nest `public enum OptionType { CALL, PUT }` inside the class.
3. Add fields: `String underlying, BigDecimal strike, BigDecimal quantity, LocalDate expiry, OptionType optionType, Currency currency`.
4. In `build()`, after the null checks: positivity on `strike` and `quantity`, then `if (expiry.isBefore(tradeDate)) throw ...`. Do NOT compare `expiry` to `LocalDate.now()`.
5. `notional()` returns `new Money(strike.multiply(quantity), currency)`.
6. `assetClass()` returns `AssetClass.DERIVATIVE`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/DerivativeTrade.java`):

```java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

public final class DerivativeTrade implements TradeType {

    public enum OptionType { CALL, PUT }

    private final TradeRef tradeRef;
    private final String underlying;
    private final BigDecimal strike;
    private final BigDecimal quantity;
    private final LocalDate expiry;
    private final OptionType optionType;
    private final Currency currency;
    private final Side side;
    private final LocalDate tradeDate;
    private final long counterpartyId;

    private DerivativeTrade(Builder b) {
        this.tradeRef       = b.tradeRef;
        this.underlying     = b.underlying;
        this.strike         = b.strike;
        this.quantity       = b.quantity;
        this.expiry         = b.expiry;
        this.optionType     = b.optionType;
        this.currency       = b.currency;
        this.side           = b.side;
        this.tradeDate      = b.tradeDate;
        this.counterpartyId = b.counterpartyId;
    }

    public static Builder builder() { return new Builder(); }

    @Override public TradeRef tradeRef()     { return tradeRef; }
    @Override public LocalDate tradeDate()   { return tradeDate; }
    @Override public AssetClass assetClass() { return AssetClass.DERIVATIVE; }
    @Override public Money notional()        { return new Money(strike.multiply(quantity), currency); }

    public String underlying()       { return underlying; }
    public BigDecimal strike()       { return strike; }
    public BigDecimal quantity()     { return quantity; }
    public LocalDate expiry()        { return expiry; }
    public OptionType optionType()   { return optionType; }
    public Currency currency()       { return currency; }
    public Side side()               { return side; }
    public long counterpartyId()     { return counterpartyId; }

    public static final class Builder {
        private TradeRef tradeRef;
        private String underlying;
        private BigDecimal strike, quantity;
        private LocalDate expiry, tradeDate;
        private OptionType optionType;
        private Currency currency;
        private Side side;
        private long counterpartyId;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder underlying(String v)        { this.underlying = v; return this; }
        public Builder strike(BigDecimal v)        { this.strike = v; return this; }
        public Builder quantity(BigDecimal v)      { this.quantity = v; return this; }
        public Builder expiry(LocalDate v)         { this.expiry = v; return this; }
        public Builder optionType(OptionType v)    { this.optionType = v; return this; }
        public Builder currency(String code)       { this.currency = Currency.getInstance(code); return this; }
        public Builder side(Side v)                { this.side = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder counterpartyId(long v)      { this.counterpartyId = v; return this; }

        public DerivativeTrade build() {
            Objects.requireNonNull(tradeRef,   "tradeRef");
            Objects.requireNonNull(underlying, "underlying");
            Objects.requireNonNull(strike,     "strike");
            Objects.requireNonNull(quantity,   "quantity");
            Objects.requireNonNull(expiry,     "expiry");
            Objects.requireNonNull(optionType, "optionType");
            Objects.requireNonNull(currency,   "currency");
            Objects.requireNonNull(side,       "side");
            Objects.requireNonNull(tradeDate,  "tradeDate");
            if (strike.signum() <= 0)   throw new IllegalStateException("strike must be > 0");
            if (quantity.signum() <= 0) throw new IllegalStateException("quantity must be > 0");
            if (expiry.isBefore(tradeDate))
                throw new IllegalStateException("expiry cannot be before tradeDate");
            return new DerivativeTrade(this);
        }
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV022 end-to-end**

After this ticket, all four sealed leaves exist and the project compiles end-to-end for the first time today. `DerivativeTrade` rejects expiry-before-trade-date but accepts an already-expired option as a valid historical record.

```bash
# from project root
./mvnw -pl backend compile
./mvnw -pl backend test -Dtest=DerivativeTradeTest
```

**Observe:**

- Full `compile` succeeds — no sealed-hierarchy warnings, no unresolved permits.
- Happy path with `optionType=CALL`, `expiry` after `tradeDate` builds and `assetClass()` returns `DERIVATIVE`.
- An `expiry` already in the past (relative to `LocalDate.now()` but after `tradeDate`) still builds — a historical option is valid data.
- An `expiry` before `tradeDate` throws `IllegalStateException("expiry cannot be before tradeDate")`.

---

### Workshop 2B — Factories, value objects, exceptions, enums

Now that the four trade types exist, you build the supporting cast: the entry point that constructs them from loosely-typed input, the value objects they hold, the exception ladder they throw, and the rule enum that drives reconciliation.

---

### TICKET-ADV023 — `TradeFactory`

**Goal:** Build a single entry point that constructs any concrete `TradeType` from a discriminator string and a parameter map, absorbing the cost of type erasure so callers stay clean.

**What**
- `model/TradeFactory.java`: `final` class, `private` constructor, single static `create(String, Map<String, Object>)` dispatching via a Java 25 switch expression over `TradeType.AssetClass` to four private helpers, one per builder from ADV019-ADV022.

**Why**
- The factory is the single boundary between loosely-typed JSON/CSV/Kafka payloads and the type-safe domain; later tickets (Day 3 Kafka consumer, Day 4 controllers) call `TradeFactory.create(...)` instead of touching individual builders.

**Observe**
- `TradeFactory.create("FOO", map)` throws `IllegalArgumentException` from `AssetClass.valueOf(...)` before any builder runs; a missing `"price"` key triggers `requireNonNull("price")` inside the matching builder with the field name in the message.

**Done when:**
- `TradeFactory` is a final class with a private constructor and a single static `create(String, Map<String, Object>)` method.
- Each known asset class string routes to the corresponding builder via a switch expression.
- An unknown discriminator, a missing key, or a wrong cast surfaces as `InvalidTradeException` with a useful message — never a raw `ClassCastException`.

<details>
<summary>Hint 1 — gentle direction</summary>

The factory is the *boundary* between the type-chaotic outside world (JSON, CSV, Kafka payloads) and the type-safe domain. Where the inside is clean, the boundary must absorb the dirt. What exception class makes that boundary visible?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use a `switch` expression on the uppercased asset-class string, with one branch per trade type and a `default` that throws. Wrap the whole switch in a `try/catch` that translates the low-level exceptions (`ClassCastException`, `NullPointerException`, `IllegalStateException`) into your `InvalidTradeException`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

You will have one public static `create` method plus four private static helpers (one per asset class). Each helper pulls typed values from the map, casts to the expected type, and feeds the corresponding builder. The `create` method's `try` block wraps the switch; the `catch` translates root cause into a single `InvalidTradeException` that includes the original message.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `TradeFactory.java` as `public final class` with a `private TradeFactory() { }` constructor.
2. Add `public static TradeType create(String assetClass, Map<String, Object> p)`.
3. Parse the discriminator with `TradeType.AssetClass.valueOf(assetClass.toUpperCase())` so an unknown value throws early.
4. Use a Java 25 switch expression on the enum value — one arm per asset class delegating to a private static helper.
5. Each helper pulls typed values from the map, calls the matching `builder()` and chains setters into `.build()`. Numerics go through `new BigDecimal(p.get(...).toString())`.
6. Let the production `try/catch` translation to `InvalidTradeException` live in the controller advice or service layer — the reference factory leaves the underlying `ClassCastException`/`IllegalStateException` to propagate.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/TradeFactory.java`):

```java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class TradeFactory {

    private TradeFactory() { }

    public static TradeType create(String assetClass, Map<String, Object> p) {
        TradeType.AssetClass ac = TradeType.AssetClass.valueOf(assetClass.toUpperCase());
        return switch (ac) {
            case EQUITY     -> equity(p);
            case FX         -> fx(p);
            case BOND       -> bond(p);
            case DERIVATIVE -> derivative(p);
        };
    }

    private static EquityTrade equity(Map<String, Object> p) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .instrumentSymbol((String) p.get("symbol"))
                .quantity(new BigDecimal(p.get("quantity").toString()))
                .price(new BigDecimal(p.get("price").toString()))
                .currency((String) p.get("currency"))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }

    private static FXTrade fx(Map<String, Object> p) {
        return FXTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .ccy1((String) p.get("ccy1"))
                .ccy2((String) p.get("ccy2"))
                .notionalCcy1(new BigDecimal(p.get("notionalCcy1").toString()))
                .fxRate(new BigDecimal(p.get("fxRate").toString()))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }

    private static BondTrade bond(Map<String, Object> p) {
        return BondTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .isin((String) p.get("isin"))
                .faceValue(new BigDecimal(p.get("faceValue").toString()))
                .couponRate(new BigDecimal(p.get("couponRate").toString()))
                .maturityDate(LocalDate.parse((String) p.get("maturityDate")))
                .currency((String) p.get("currency"))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }

    private static DerivativeTrade derivative(Map<String, Object> p) {
        return DerivativeTrade.builder()
                .tradeRef(TradeRef.of((String) p.get("tradeRef")))
                .underlying((String) p.get("underlying"))
                .strike(new BigDecimal(p.get("strike").toString()))
                .quantity(new BigDecimal(p.get("quantity").toString()))
                .expiry(LocalDate.parse((String) p.get("expiry")))
                .optionType(DerivativeTrade.OptionType.valueOf((String) p.get("optionType")))
                .currency((String) p.get("currency"))
                .side(Side.valueOf((String) p.get("side")))
                .tradeDate(LocalDate.parse((String) p.get("tradeDate")))
                .counterpartyId(((Number) p.get("counterpartyId")).longValue())
                .build();
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV023 end-to-end**

After this ticket, `TradeFactory.create("EQUITY", map)` returns a typed `EquityTrade` from a loosely-typed `Map<String, Object>`, and unknown discriminators / bad casts surface cleanly (either as `IllegalArgumentException` from `valueOf` or — once translation is wired in ADV025 — as `InvalidTradeException`).

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeFactoryTest
```

**Observe:**

- `TradeFactory.create("EQUITY", validMap)` returns a non-null `EquityTrade`; `instanceof EquityTrade` is true.
- `TradeFactory.create("FOO", map)` throws `IllegalArgumentException` (from `AssetClass.valueOf`) before any builder is touched.
- A missing key like `"price"` triggers an `NullPointerException` inside the builder's `requireNonNull("price")` — the named message points at the right field.
- `TradeFactory` has a private no-arg constructor and exposes only `static` methods (no instance state).

---

### TICKET-ADV024 — `Money` and `TradeRef` value objects

**Goal:** Introduce two immutable value objects: `Money` (amount plus currency) and `TradeRef` (regex-validated trade reference).

**What**
- Two records in `com.dbtraining.reconx.model`: `Money(BigDecimal amount, Currency currency)` with a compact constructor rejecting negatives plus a `plus(Money)` that throws on currency mismatch, and `TradeRef(String value)` validated against `^[A-Z]{3}-\d{8}-\d{4}$` in its compact constructor.

**Why**
- A bare `String` "trade reference" can be confused with any other string — counterparty, symbol, ISIN; the typed `TradeRef` makes those mix-ups a compile error, and is the key ADV028's `equals`/`hashCode` will hash on.

**Observe**
- `Money.of("100","USD").plus(Money.of("50","EUR"))` throws `IllegalArgumentException` with a "currency mismatch" message; `TradeRef.of("foo")` throws naming the expected `AAA-YYYYMMDD-NNNN` format.

**Done when:**
- Both types are records, not classes with setters.
- `Money` rejects negative amounts and mismatched currencies in its `add` operation; the currency is `java.util.Currency`, not `String`.
- `TradeRef` validates its string form against the platform pattern (three uppercase letters, dash, eight digits, dash, four digits) in its compact constructor.

<details>
<summary>Hint 1 — gentle direction</summary>

A value object's identity is its value. Two `Money` instances with the same amount and currency *are* the same money. What Java feature gives you value-based equality, immutability, and a compact constructor in one keyword?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Records have a *compact constructor* (the constructor body without an argument list) — that is the right place for validation. For `TradeRef`, use `java.util.regex.Pattern` with anchors (`^` and `$`) and an explicit character class for the asset-class prefix.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`Money` is a record of `(BigDecimal amount, Currency currency)` with a compact constructor that null-checks both and rejects negative amounts, plus a static `of(String, String)` convenience and an `add(Money other)` method that rejects mismatched currencies. `TradeRef` is a record of `(String value)` with a static `Pattern` constant and a compact constructor that throws if the value does not match. Override `toString` on `TradeRef` to return the value.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `Money.java` as `public record Money(BigDecimal amount, Currency currency)`.
2. Add a compact constructor that null-checks both fields and rejects negative `amount.signum() < 0`.
3. Add static factory `Money.of(String amount, String currencyCode)` returning a new `Money`.
4. Add `plus(Money other)` returning a new `Money`, throwing on currency mismatch. Name it `plus` (not `add`) to match the reference.
5. Create `TradeRef.java` as `public record TradeRef(String value)` with a `static final Pattern PATTERN = Pattern.compile("^[A-Z]{3}-\\d{8}-\\d{4}$")`.
6. Compact constructor null-checks `value` and throws if `!PATTERN.matcher(value).matches()`. Override `toString()` to return `value`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/Money.java`):

```java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * ============================================================================
 * Immutable value object: Money
 *
 * WHAT:    Record bundling a {@link BigDecimal} amount with a {@link Currency}.
 *          Used everywhere a monetary value crosses a boundary (DTO, event,
 *          metric).
 * HOW:     Compact constructor enforces: non-null amount, non-null currency,
 *          non-negative amount. {@link BigDecimal} (not double) prevents
 *          accumulating floating-point error on aggregations.
 * WHY:     Passing raw BigDecimal around loses currency context — a USD 100
 *          can be silently added to a EUR 100. Money makes the mismatch
 *          fail at the type level: {@code plus()} throws if currencies differ.
 * OBSERVE: {@code Money.of("100.00","USD").plus(Money.of("50","EUR"))} throws.
 *          {@code Money.of("100","USD").plus(Money.of("50","USD"))} returns 150 USD.
 * ============================================================================
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
        }
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /** Add another Money of the same currency. Throws on currency mismatch. */
    public Money plus(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add %s to %s — currency mismatch".formatted(other.currency, this.currency));
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money times(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier), this.currency);
    }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/TradeRef.java`):

```java
package com.dbtraining.reconx.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * Immutable value object: TradeRef (natural key for a trade)
 *
 * WHAT:    Strongly-typed wrapper around the trade reference string. Format:
 *          AAA-YYYYMMDD-NNNN  (3 letters, 8-digit date, 4 digits).
 * HOW:     Compact constructor validates against the regex; null and bad
 *          formats fail at construction.
 * WHY:     A bare String "trade reference" can be confused with any other
 *          String — counterparty name, instrument symbol. TradeRef as a
 *          distinct type makes those mix-ups a compile error.
 * OBSERVE: TradeRef.of("EQU-20260602-0001") works; .of("foo") throws.
 * ============================================================================
 */
public record TradeRef(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z]{3}-\\d{8}-\\d{4}$");

    public TradeRef {
        Objects.requireNonNull(value, "tradeRef value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tradeRef format '%s' — expected AAA-YYYYMMDD-NNNN".formatted(value));
        }
    }

    public static TradeRef of(String value) {
        return new TradeRef(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV024 end-to-end**

After this ticket, `Money` and `TradeRef` are records, validate in their compact constructors, and provide value-based equality plus immutable arithmetic (`Money.plus` returns a new instance).

```bash
# from project root
./mvnw -pl backend test -Dtest=MoneyTest,TradeRefTest
```

**Observe:**

- `Money.of("100","USD").equals(Money.of("100","USD"))` is true; same values, same money.
- `Money.of("100","USD").plus(Money.of("50","USD"))` returns a *new* `Money(150, USD)`; the original receivers are unchanged.
- `Money.of("100","USD").plus(Money.of("50","EUR"))` throws `IllegalArgumentException` with a clear "currency mismatch" message.
- `TradeRef.of("EQU-20260602-0001")` succeeds; `TradeRef.of("foo")` and `TradeRef.of(null)` both throw with a message naming the expected format `AAA-YYYYMMDD-NNNN`.

---

### TICKET-ADV025 — Exception hierarchy

**Goal:** Define the platform's exception ladder rooted at an abstract `ReconException`, with four concrete subtypes covering construction, lookup, duplication, and reconciliation outcomes.

**What**
- Five files in `com.dbtraining.reconx.exception`: `abstract ReconException extends RuntimeException` with `(String)` and `(String, Throwable)` protected constructors, plus `InvalidTradeException`, `TradeNotFoundException`, `DuplicateTradeRefException`, `ReconciliationMismatchException` each delegating both constructors up.

**Why**
- One abstract root lets a single `@RestControllerAdvice` `catch (ReconException)` map every domain subtype to an RFC-7807 `ProblemDetail` when Day 5 wires the controller advice — no per-type handler explosion.

**Observe**
- `new ReconException("x")` is a compile error (the class is `abstract`); `find backend/src/main/java/com/dbtraining/reconx/exception -name '*.java'` lists exactly five files.

**Done when:**
- `ReconException` is abstract and extends `RuntimeException` (unchecked).
- Four concrete subtypes exist: `InvalidTradeException`, `TradeNotFoundException`, `DuplicateTradeRefException`, `ReconciliationMismatchException`.
- Each subtype has both a `(String)` and a `(String, Throwable)` constructor — chained causes must not be lost.
- All exception classes live in `com.dbtraining.reconx.exception`.

<details>
<summary>Hint 1 — gentle direction</summary>

In a Spring service, the default for custom exceptions is unchecked, not checked. Ask yourself why — what does `@RestControllerAdvice` give you for free, and what cost would checked exceptions impose on every layer?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at `RuntimeException`'s constructors — you want both the message-only and message-plus-cause forms exposed through your hierarchy. The root class is abstract so nobody throws a bare `ReconException`; concrete subtypes carry domain meaning.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Five files: `ReconException` (abstract, two protected constructors delegating to `super`), and four concrete `extends ReconException` subtypes each with two public constructors delegating up. No fields, no extra methods — the type itself is the signal.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create the package `com.dbtraining.reconx.exception` if it does not exist.
2. Create `ReconException.java` as `public abstract class ReconException extends RuntimeException` with two `protected` constructors: `(String)` and `(String, Throwable)`, each delegating to `super`.
3. Create `InvalidTradeException.java` extending `ReconException` (semantically 400 Bad Request).
4. Create `TradeNotFoundException.java` extending `ReconException` (semantically 404 Not Found).
5. Create `DuplicateTradeRefException.java` extending `ReconException` (semantically 409 Conflict).
6. Create `ReconciliationMismatchException.java` extending `ReconException` (semantically 422 Unprocessable).
7. Wire HTTP status mapping in the `@RestControllerAdvice` later — Day 5.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/exception/ReconException.java`):

```java
package com.dbtraining.reconx.exception;

/**
 * ============================================================================
 * Root of the exception hierarchy
 *
 * WHAT:    Abstract parent for every domain-level exception raised by the
 *          reconciliation service.
 * HOW:     Extends RuntimeException (we don't want checked-exception noise
 *          on the controller signatures). All subclasses go in this package.
 * WHY:     One root means @RestControllerAdvice can `catch (ReconException)`
 *          and map every domain-specific subtype to an RFC-7807 ProblemDetail
 *          without an explicit handler per type.
 * ============================================================================
 */
public abstract class ReconException extends RuntimeException {
    protected ReconException(String message) { super(message); }
    protected ReconException(String message, Throwable cause) { super(message, cause); }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/exception/InvalidTradeException.java`):

```java
package com.dbtraining.reconx.exception;

/** 400 Bad Request: a trade failed business validation. */
public class InvalidTradeException extends ReconException {
    public InvalidTradeException(String message) { super(message); }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/exception/TradeNotFoundException.java`):

```java
package com.dbtraining.reconx.exception;

/** 404 Not Found: tradeRef has no row in trades. */
public class TradeNotFoundException extends ReconException {
    public TradeNotFoundException(String tradeRef) {
        super("Trade not found: " + tradeRef);
    }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/exception/DuplicateTradeRefException.java`):

```java
package com.dbtraining.reconx.exception;

/** 409 Conflict: tradeRef already exists. */
public class DuplicateTradeRefException extends ReconException {
    public DuplicateTradeRefException(String tradeRef) {
        super("Duplicate tradeRef: " + tradeRef);
    }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/exception/ReconciliationMismatchException.java`):

```java
package com.dbtraining.reconx.exception;

/** 422 Unprocessable: internal vs external trade do not match. */
public class ReconciliationMismatchException extends ReconException {
    public ReconciliationMismatchException(String message) { super(message); }
}
```

</details>

**▶ Run the project — verify TICKET-ADV025 end-to-end**

After this ticket, the four-level exception ladder compiles, every subtype extends `ReconException` (which is abstract), and the message + cause are preserved through the chain.

```bash
# from project root
./mvnw -pl backend test -Dtest=ReconExceptionTest
```

**Observe:**

- Every subtype `extends ReconException` — verify with `javap -p backend/target/classes/com/dbtraining/reconx/exception/InvalidTradeException.class` (and the other three).
- `ReconException` itself cannot be instantiated directly — `new ReconException("x")` is a compile error because the class is `abstract`.
- A `new InvalidTradeException("bad trade", rootCause)` preserves the cause: `e.getCause() == rootCause` is true.
- All five classes sit in `com.dbtraining.reconx.exception` — `find backend/src/main/java/com/dbtraining/reconx/exception -name '*.java'` lists exactly five files.

---

### TICKET-ADV026 — `ReconciliationRule` enum

**Goal:** Model the closed set of reconciliation tolerance rules as an enum, with thresholds and a behaviour method on each constant.

**What**
- `model/ReconciliationRule.java`: an enum with at least four constants carrying `BigDecimal` thresholds via a private constructor, and a `matches(BigDecimal, BigDecimal, BigDecimal, BigDecimal)` method comparing two `(price, quantity)` pairs using `compareTo` only.

**Why**
- Modelling tolerance rules as enum constants with data and behaviour collapses what would be a strategy-pattern class hierarchy into one file, and is the lookup table the parallel reconciliation engine on Day 3 dispatches against.

**Observe**
- `grep -E "double|float" model/ReconciliationRule.java` returns no hits; `ReconciliationRule.EXACT.matches(...)` returns `false` for any non-zero drift, while looser constants pass within their threshold.

**Done when:**
- `ReconciliationRule` is an enum with at least four constants (exact, price tolerance, quantity tolerance, loose) carrying `BigDecimal` thresholds.
- A `matches(...)` method compares two `(price, quantity)` pairs against the constant's tolerances.
- No `double` anywhere — financial comparisons use `BigDecimal` only.

<details>
<summary>Hint 1 — gentle direction</summary>

An enum constant is itself an object — it can carry data and behaviour. Ask yourself how this collapses what would otherwise be a strategy-pattern hierarchy of classes into a single file.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at enums with constructors: pass the thresholds in to each constant, store them as private final fields, expose them via accessors. The `matches` method uses `BigDecimal.compareTo`, never `==` or `equals`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`ReconciliationRule` has a constructor taking `(BigDecimal priceTolerancePct, BigDecimal qtyToleranceAbs)`, four constants invoking it with literal values, two accessors, and a `matches(BigDecimal priceA, BigDecimal priceB, BigDecimal qtyA, BigDecimal qtyB)` boolean method. Inside `matches`, compute price drift as a percentage with explicit `RoundingMode.HALF_UP`, then compare each drift to its tolerance with `compareTo(...) <= 0`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `ReconciliationRule.java` as a `public enum` with `private final BigDecimal priceTolerancePct, qtyToleranceAbs`.
2. Add a package-private constructor taking both `BigDecimal` fields.
3. Declare five constants: `EXACT`, `PRICE_TOLERANCE_1PCT`, `PRICE_TOLERANCE_50BPS`, `QTY_TOLERANCE_5UNITS`, `LOOSE` — each calling the constructor with literal `BigDecimal`s.
4. Add accessors `priceTolerancePct()` and `qtyToleranceAbs()`.
5. Implement `matches(internalPrice, internalQty, externalPrice, externalQty)`: take absolute price diff, divide by `internalPrice` with scale 6 and `RoundingMode.HALF_UP`, guard against zero division.
6. Combine `priceOk && qtyOk` using `compareTo(...) <= 0`. No `==`, no `double`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/ReconciliationRule.java`):

```java
package com.dbtraining.reconx.model;

import java.math.BigDecimal;

/**
 * ============================================================================
 * ReconciliationRule enum with configurable thresholds
 *
 * WHAT:    Each enum value carries its own price tolerance (%) and quantity
 *          tolerance (absolute units). {@link #matches} returns true if the
 *          internal vs external trade pair is within tolerance.
 * HOW:     Enum-with-state pattern — instance fields + a behaviour method.
 * WHY:     Putting the rule on the enum keeps "what is a match" co-located
 *          with the rule's name, so the reconciliation engine is just:
 *          `if (rule.matches(internal, external)) ... matched ...`.
 * OBSERVE: PRICE_TOLERANCE_1PCT.matches(p, p*1.005) is true; *1.02 is false.
 * ============================================================================
 */
public enum ReconciliationRule {

    EXACT(BigDecimal.ZERO, BigDecimal.ZERO),
    PRICE_TOLERANCE_1PCT(new BigDecimal("0.01"), BigDecimal.ZERO),
    PRICE_TOLERANCE_50BPS(new BigDecimal("0.005"), BigDecimal.ZERO),
    QTY_TOLERANCE_5UNITS(BigDecimal.ZERO, new BigDecimal("5")),
    LOOSE(new BigDecimal("0.05"), new BigDecimal("10"));

    private final BigDecimal priceTolerancePct;
    private final BigDecimal qtyToleranceAbs;

    ReconciliationRule(BigDecimal priceTolerancePct, BigDecimal qtyToleranceAbs) {
        this.priceTolerancePct = priceTolerancePct;
        this.qtyToleranceAbs   = qtyToleranceAbs;
    }

    public BigDecimal priceTolerancePct() { return priceTolerancePct; }
    public BigDecimal qtyToleranceAbs()   { return qtyToleranceAbs; }

    /**
     * Decide whether two prices/quantities are within this rule's tolerance.
     * @return true if BOTH the price diff (as %) AND the qty diff (as abs)
     *         are within tolerance.
     */
    public boolean matches(BigDecimal internalPrice, BigDecimal internalQty,
                           BigDecimal externalPrice, BigDecimal externalQty) {
        BigDecimal priceDiff = internalPrice.subtract(externalPrice).abs();
        BigDecimal priceDiffPct = internalPrice.signum() == 0
                ? BigDecimal.ZERO
                : priceDiff.divide(internalPrice, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal qtyDiff = internalQty.subtract(externalQty).abs();

        boolean priceOk = priceDiffPct.compareTo(priceTolerancePct) <= 0;
        boolean qtyOk   = qtyDiff.compareTo(qtyToleranceAbs) <= 0;
        return priceOk && qtyOk;
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV026 end-to-end**

After this ticket, `ReconciliationRule` is an enum whose constants each carry their own tolerances, and `matches(...)` correctly applies them with `BigDecimal.compareTo` — no `double`, no `==` on numerics.

```bash
# from project root
./mvnw -pl backend test -Dtest=ReconciliationRuleTest
```

**Observe:**

- `ReconciliationRule.EXACT.matches(p, q, p, q)` is true; any drift returns false.
- `PRICE_TOLERANCE_1PCT.matches(100, 10, 100.5, 10)` is true (0.5 % drift), but `.matches(100, 10, 102, 10)` is false (2 %).
- `QTY_TOLERANCE_5UNITS.matches(100, 10, 100, 14)` is true; `.matches(100, 10, 100, 16)` is false.
- `grep -E "\\b(double|float)\\b" backend/src/main/java/com/dbtraining/reconx/model/ReconciliationRule.java` returns nothing.

---

### Workshop 2C — Contract methods, validation, docs, PR review

The final stretch. You add the contract methods that integrate your trades with the JDK collections (`Comparable`, `equals`, `hashCode`), declarative validation on the inbound DTO, PII-safe logging, full Javadoc, and a peer-reviewed PR.

---

### TICKET-ADV027 — `Comparable<TradeType>` natural ordering

**Goal:** Give `TradeType` a natural ordering that is safe for `TreeSet` and `TreeMap` and consistent with the equality you will add in TICKET-ADV028.

**What**
- `TradeType` declared `extends Comparable<TradeType>`, with a `static final Comparator<TradeType> NATURAL` built from `comparing(TradeType::tradeDate).reversed().thenComparing(t -> t.tradeRef().value())` and a `default int compareTo(...)` delegating to it.

**Why**
- Ordering and equality must agree, or `TreeSet`/`TreeMap` behave bizarrely — the `tradeRef` tiebreaker keeps natural ordering consistent with the `equals` implementation landing in ADV028.

**Observe**
- A `TreeSet<TradeType>` built from one of each asset class iterates newest-first by `tradeDate` with no `NullPointerException`; `t1.compareTo(t2) == 0` is true only when `t1.tradeRef().equals(t2.tradeRef())`.

**Done when:**
- `TradeType` extends `Comparable<TradeType>`.
- Natural ordering is newest-trade-date first, ties broken by `TradeRef` ascending.
- A heterogeneous list of trades (equity, FX, bond, derivative) sorts deterministically with no NullPointerException.

<details>
<summary>Hint 1 — gentle direction</summary>

Ordering and equality must agree, or `TreeSet` behaves bizarrely. Decide what your equality is going to be keyed on (preview TICKET-ADV028) before you finalise the ordering — then make sure the comparator returns zero only when those two trades are equal.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at `java.util.Comparator` — specifically `Comparator.comparing(...)`, `reverseOrder()`, and `thenComparing(...)`. You can build the comparator as a `static final` constant on the interface and delegate `compareTo` to it via a default method.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

On the interface, add `extends Comparable<TradeType>`, declare a `static final Comparator<TradeType> NATURAL` built from `comparing(TradeType::tradeDate, reverseOrder()).thenComparing(t -> t.tradeRef().value())`, and provide a default `compareTo` that delegates to `NATURAL.compare(this, other)`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `TradeType.java` and add `extends Comparable<TradeType>` to the sealed interface declaration.
2. Import `java.util.Comparator`.
3. Declare `Comparator<TradeType> NATURAL = Comparator.comparing(TradeType::tradeDate).reversed().thenComparing(t -> t.tradeRef().value())` as a `static final` field on the interface.
4. Add a `default int compareTo(TradeType other) { return NATURAL.compare(this, other); }`.
5. Tie-breaking on `tradeRef.value()` guarantees a deterministic total order, which keeps equality + ordering aligned for TICKET-ADV028.
6. Sanity check with a heterogeneous `TreeSet<TradeType>` containing one of each asset class — iteration must be newest-first, no NPE.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/model/TradeType.java`):

```java
package com.dbtraining.reconx.model;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * ============================================================================
 * Sealed interface TradeType
 *
 * WHAT:    Sealed root of the trade hierarchy. Only the four named
 *          permitted classes can implement it. Any new asset class needs an
 *          explicit code change here — by design.
 * HOW:     `sealed ... permits ...` on Java 25.
 * WHY:     Without sealing, anyone could write their own `Trade` subclass and
 *          slip through the reconciliation engine's pattern-matching switch.
 *          Sealing turns the engine's switch into an exhaustive one — the
 *          compiler enforces that every case is handled.
 * OBSERVE: Removing `permits BondTrade` causes a compile error in
 *          ReconciliationEngine's switch expression.
 * HINT:    See Day 2 trainer guide §"Workshop 2A — sealed hierarchy" for the
 *          design discussion.
 * ============================================================================
 *
 * Comparable natural ordering (most-recent trade first)
 * equals/hashCode based on tradeRef (the natural key)
 *
 * Comparator lives on the sealed interface, so every impl shares the same
 * ordering rule — there is no per-class compareTo override to forget to
 * update when adding a new field.
 */
public sealed interface TradeType
        extends Comparable<TradeType>
        permits EquityTrade, FXTrade, BondTrade, DerivativeTrade {

    /** Stable natural key. Drives equals/hashCode. */
    TradeRef tradeRef();

    /** Notional value of the trade for reconciliation summaries. */
    Money notional();

    /** Business date the trade was struck on. */
    LocalDate tradeDate();

    /** Discriminator for switch expressions and persistence mapping. */
    AssetClass assetClass();

    Comparator<TradeType> NATURAL = Comparator
            .comparing(TradeType::tradeDate).reversed()
            .thenComparing(t -> t.tradeRef().value());

    @Override
    default int compareTo(TradeType other) {
        return NATURAL.compare(this, other);
    }

    enum AssetClass { EQUITY, FX, BOND, DERIVATIVE }
}
```

</details>

**▶ Run the project — verify TICKET-ADV027 end-to-end**

After this ticket, `TradeType` extends `Comparable<TradeType>`, a shared `NATURAL` comparator orders newest trade date first with `tradeRef` as the tiebreaker, and a heterogeneous `TreeSet<TradeType>` sorts deterministically.

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeTypeOrderingTest
```

**Observe:**

- A `TreeSet<TradeType>` built from one Equity / FX / Bond / Derivative trade iterates newest-first by `tradeDate`, no `NullPointerException`.
- Two trades sharing the same `tradeDate` are ordered by `tradeRef.value()` ascending — stable, repeatable.
- `t1.compareTo(t2) == 0` is true *only* when `t1.tradeRef().equals(t2.tradeRef())` — keeping ordering consistent with the equality you wire in ADV028.
- `javap -p backend/target/classes/com/dbtraining/reconx/model/TradeType.class` shows `Comparable` in the supertypes.

---

### TICKET-ADV028 — `equals` and `hashCode` keyed on `tradeRef`

**Goal:** Implement value-based equality on every concrete trade, keyed on the natural business key (`tradeRef`), so collections behave correctly pre- and post-persist.

**What**
- An `equals` override on each of `EquityTrade`, `FXTrade`, `BondTrade`, `DerivativeTrade` using `instanceof` pattern matching against the concrete class and comparing `tradeRef`, plus a `hashCode()` returning `tradeRef.hashCode()`.

**Why**
- The natural business key (`tradeRef`) is validated at construction and never null, unlike a surrogate DB id which is nullable pre-persist — keying equality on `tradeRef` keeps a domain object equal to itself across the persist boundary, and is the rule ADV027's comparator is already consistent with.

**Observe**
- A `HashSet<TradeType>` containing two trades with the same `tradeRef` but different other fields has `.size() == 1`; overriding only `equals` (and not `hashCode`) would break `HashMap` lookups — keep them in lockstep.

**Done when:**
- Every concrete trade overrides both `equals` and `hashCode`.
- Equality is keyed on `tradeRef` alone — not on `id`, not on every field.
- A `HashSet<TradeType>` of two trades with the same `tradeRef` but different other fields has size 1.

<details>
<summary>Hint 1 — gentle direction</summary>

There are two candidate keys: the surrogate database id (auto-generated, nullable pre-persist) and the natural business key (`tradeRef`, validated at construction, never null). Which one is stable across the full lifecycle of a domain object?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at pattern-matching `instanceof` — `o instanceof TradeType other` lets you match across the sealed hierarchy in one line. Override both `equals` and `hashCode` together; one without the other breaks `HashMap`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`equals` is: self-check, pattern-match `instanceof TradeType other`, compare `this.tradeRef` to `other.tradeRef()`. `hashCode` returns `tradeRef.hashCode()`. Same body in every concrete class — you may copy-paste today; a shared `Trade.equals/hashCode` is a refactor for later.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `EquityTrade.java` and add an `equals` override using `instanceof` pattern matching against the concrete class.
2. Add `hashCode()` returning `tradeRef.hashCode()`.
3. Repeat the same two methods in `FXTrade`, `BondTrade`, `DerivativeTrade` — body is identical aside from the type in the `instanceof` check.
4. Verify with `new HashSet<>(List.of(t1, t2))` where both share a `tradeRef` but differ on other fields — `.size()` must be 1.
5. The reference uses the concrete class in `instanceof` (not the interface) — it is more specific and matches the per-class override pattern.

**Reference solution** (`EquityTrade.java`, append inside the class):

```java
@Override
public boolean equals(Object o) {
    return (o instanceof EquityTrade other) && tradeRef.equals(other.tradeRef);
}

@Override public int hashCode() { return tradeRef.hashCode(); }
```

**Reference solution** (`FXTrade.java`, append inside the class):

```java
@Override public boolean equals(Object o) {
    return (o instanceof FXTrade other) && tradeRef.equals(other.tradeRef);
}
@Override public int hashCode() { return tradeRef.hashCode(); }
```

**Reference solution** (`BondTrade.java`, append inside the class):

```java
@Override public boolean equals(Object o) {
    return (o instanceof BondTrade other) && tradeRef.equals(other.tradeRef);
}
@Override public int hashCode() { return tradeRef.hashCode(); }
```

**Reference solution** (`DerivativeTrade.java`, append inside the class):

```java
@Override public boolean equals(Object o) {
    return (o instanceof DerivativeTrade other) && tradeRef.equals(other.tradeRef);
}
@Override public int hashCode() { return tradeRef.hashCode(); }
```

</details>

**▶ Run the project — verify TICKET-ADV028 end-to-end**

After this ticket, every concrete trade overrides `equals` and `hashCode` keyed on `tradeRef`, so a `HashSet<TradeType>` of two trades that share a `tradeRef` (but differ on other fields) has size 1.

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeEqualityTest
```

**Observe:**

- `new HashSet<>(List.of(t1, t2))` has size **1** when `t1.tradeRef().equals(t2.tradeRef())`, even if quantity / price differ.
- `t1.equals(t2)` ⇔ `t1.hashCode() == t2.hashCode()` — the contract holds for every concrete trade.
- `t1.equals(t2)` is consistent with `t1.compareTo(t2) == 0` from ADV027 — same key, same semantics.
- Cross-type equality is `false`: an `EquityTrade` and an `FXTrade` with the *same* `tradeRef` are not equal (each `equals` body uses concrete-class `instanceof`).

---

### TICKET-ADV029 — JSR-380 validation on the request DTO

**Goal:** Add declarative validation to the inbound HTTP DTO, leaving the domain Builder as the second line of defence.

**What**
- `dto/TradeRequest.java`: a record in `com.dbtraining.reconx.dto` with `jakarta.validation.constraints` annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@PositiveOrZero`, `@Pattern`) on every component, one annotation per line.

**Why**
- The DTO is the wire contract — putting JSR-380 annotations on the JPA entity would couple persistence to wire format; Day 4 controllers will fire these via `@Valid @RequestBody`, leaving the domain Builder from ADV019 as the second line of defence.

**Observe**
- `grep "javax.validation" dto/TradeRequest.java` returns nothing (must be `jakarta.*`); `Validator.validate(...)` on a request with `quantity = -1` returns exactly one violation tagged with the `@Positive` message.

**Done when:**
- `TradeRequest` is a record in `com.dbtraining.reconx.dto` with JSR-380 (`jakarta.validation.constraints`) annotations on every field.
- Annotations match field types correctly — `@NotNull` and `@Positive` on `BigDecimal`, `@NotBlank` and `@Pattern` on `String`, etc.
- A malformed request would be rejected at the controller boundary; the domain Builder would still catch anything that slipped through.

<details>
<summary>Hint 1 — gentle direction</summary>

You have two validation layers. The HTTP boundary needs declarative, framework-driven validation; the domain needs imperative invariant enforcement. Which annotations live on which type? Putting them in the wrong place means they never fire.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at the `jakarta.validation.constraints` package — `@NotNull`, `@NotBlank`, `@Positive`, `@PositiveOrZero`, `@Pattern`, `@Size`. `@NotEmpty` is for collections and strings, not `BigDecimal`. Put the annotations on a record component declaration, one per line for readability.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`TradeRequest` has roughly seven components: `tradeRef` (NotNull + Pattern), `instrumentSymbol` (NotBlank + Size), `quantity` (NotNull + Positive), `price` (NotNull + PositiveOrZero), `tradeDate` (NotNull), `currency` (NotBlank + Size 3), `side` (NotBlank + Pattern matching BUY|SELL). Each component declaration carries its annotations; the body is empty (`{}`).

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java`.
2. Import `jakarta.validation.constraints.*` (NOT `javax.validation`).
3. Declare `public record TradeRequest(...)` with one parameter per request field.
4. Annotate every component on its own line: `@NotNull` for objects, `@NotBlank` for strings, `@Positive`/`@PositiveOrZero` for `BigDecimal`, `@Pattern` for ref/side regex.
5. Body is `{}` — no extra methods.
6. Wire `@Valid @RequestBody TradeRequest` on the controller method later (Day 3+); validation only fires when `@Valid` is on the parameter.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java`):

```java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ============================================================================
 * TradeRequest DTO (POST body)
 * JSR-380 validation annotations live on the DTO, not the entity
 *
 * WHY:    Putting @Pattern/@Positive/@NotNull on the JPA entity couples
 *         persistence to wire format. The DTO is the wire contract; validate
 *         it before mapping.
 * ============================================================================
 */
public record TradeRequest(
        @NotNull
        @Pattern(regexp = "^[A-Z]{3}-\\d{8}-\\d{4}$",
                 message = "tradeRef must match AAA-YYYYMMDD-NNNN")
        String tradeRef,

        @NotNull
        Long instrumentId,

        @NotNull
        Long counterpartyId,

        @NotBlank
        String assetClass,

        @NotBlank
        @Pattern(regexp = "^(BUY|SELL)$")
        String side,

        @NotNull @Positive
        BigDecimal quantity,

        @NotNull @PositiveOrZero
        BigDecimal price,

        @NotNull
        LocalDate tradeDate
) {}
```

</details>

**▶ Run the project — verify TICKET-ADV029 end-to-end**

After this ticket, `TradeRequest` compiles as a record in `com.dbtraining.reconx.dto` with JSR-380 annotations on every component; the controller side will be wired up in Day 3+.

```bash
# from project root
./mvnw -pl backend compile
./mvnw -pl backend test -Dtest=TradeRequestValidationTest
```

**Observe:**

- All imports come from `jakarta.validation.constraints.*` — `grep "javax.validation" backend/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java` returns nothing.
- A `Validator.validate(...)` call against a well-formed `TradeRequest` returns zero violations; a request with `quantity = -1` returns exactly one violation on `quantity` with the `@Positive` message.
- A bad `tradeRef` like `"foo"` produces a violation tagged with the regex message `"tradeRef must match AAA-YYYYMMDD-NNNN"`.
- Annotations live only on the DTO; no `jakarta.validation` import appears in any class under `com.dbtraining.reconx.model`.

---

### TICKET-ADV030 — PII-safe `toString` for logging

**Goal:** Override `toString` on every concrete trade so that default logging never leaks counterparty or settlement PII.

**What**
- A hand-written `toString()` override on every concrete trade (`EquityTrade`, `FXTrade`, `BondTrade`, `DerivativeTrade`) including `tradeRef`, instrument identifier, commercial numerics, and `side` — formatted via `String.formatted(...)` with `BigDecimal.toPlainString()` and excluding `counterpartyId`.

**Why**
- IDE-generated `toString` over every field leaks counterparty/settlement PII into log lines; an explicit hand-written `toString` makes the omission deliberate, with a `// NOTE:` comment so future maintainers don't "fix" it.

**Observe**
- `assertThat(trade.toString()).doesNotContain(String.valueOf(trade.counterpartyId()))` passes on every concrete trade; `BigDecimal` fields render in plain (e.g. `100.50`), never in scientific notation (`1.005E2`).

**Done when:**
- Each concrete trade has a hand-written `toString` (not IDE-generated over every field).
- The string includes `tradeRef`, instrument identifier, and the public commercial fields — but excludes counterparty references, full notional in settlement currency, and any LEI-like identifier.
- `BigDecimal` fields render via `toPlainString()`, not the default `toString` (which can use scientific notation).

<details>
<summary>Hint 1 — gentle direction</summary>

The default `toString` from your IDE includes every field. Some of those fields are regulatory hazards in plain-text logs. Ask yourself what the right *default* is — and what an explicit opt-in alternative looks like for the rare case where you do need full detail.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `String.formatted(...)` (the instance method on `String`) or `String.format(...)` with a clear template. Include a code comment explaining what was deliberately omitted and why — future maintainers will be tempted to add fields back.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

For `EquityTrade`: a formatted string with `ref`, `symbol`, `qty`, `price`, `side` only — five fields, none of them PII. `BigDecimal` values use `.toPlainString()`. The method ends with a `// NOTE:` comment listing the fields you intentionally left out and pointing to the audit-formatter for full detail.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In each concrete trade, override `toString()` by hand — never let the IDE generate-over-all-fields.
2. Use `String.formatted(...)` with a clear template naming each field for log-readability.
3. Include `tradeRef`, the public instrument identifier (symbol / ccy pair / isin / underlying), commercial numerics (qty, price / notional / face / strike), and `side`.
4. Exclude `counterpartyId` everywhere — that is the PII line in this codebase.
5. Add a `// NOTE:` comment listing what was deliberately omitted so a future maintainer does not "fix" the omission.
6. Confirm with `assertThat(trade.toString()).doesNotContain(String.valueOf(trade.counterpartyId()))`.

**Reference solution** (`EquityTrade.toString`):

```java
@Override
public String toString() {
    return "EquityTrade[ref=%s, symbol=%s, qty=%s, price=%s %s, side=%s]"
            .formatted(tradeRef, instrumentSymbol, quantity, price, currency.getCurrencyCode(), side);
}
```

**Reference solution** (`FXTrade.toString`):

```java
@Override public String toString() {
    return "FXTrade[ref=%s, %s/%s, notional=%s %s, rate=%s, side=%s]"
            .formatted(tradeRef, ccy1.getCurrencyCode(), ccy2.getCurrencyCode(),
                       notionalCcy1, ccy1.getCurrencyCode(), fxRate, side);
}
```

**Reference solution** (`BondTrade.toString`):

```java
@Override public String toString() {
    return "BondTrade[ref=%s, isin=%s, face=%s %s, coupon=%s, maturity=%s, side=%s]"
            .formatted(tradeRef, isin, faceValue, currency.getCurrencyCode(),
                       couponRate, maturityDate, side);
}
```

**Reference solution** (`DerivativeTrade.toString`):

```java
@Override public String toString() {
    return "DerivativeTrade[ref=%s, %s %s on %s, strike=%s %s, qty=%s, expiry=%s, side=%s]"
            .formatted(tradeRef, optionType, underlying, tradeDate, strike,
                       currency.getCurrencyCode(), quantity, expiry, side);
}
```

</details>

**▶ Run the project — verify TICKET-ADV030 end-to-end**

After this ticket, every concrete trade has a hand-written `toString` that includes commercial fields but excludes `counterpartyId` — the platform's PII line.

```bash
# from project root
./mvnw -pl backend test -Dtest=TradeToStringTest
```

**Observe:**

- For each concrete trade `t`, `t.toString().contains(String.valueOf(t.counterpartyId()))` is **false**.
- `EquityTrade.toString()` includes `ref`, `symbol`, `qty`, `price`, currency code, and `side` — no other fields.
- `BigDecimal` fields render as plain decimals (no `1E+2` scientific notation) — i.e. backed by `toPlainString()` or a format spec.
- A `// NOTE:` comment in each `toString` lists the deliberately-omitted fields so a future maintainer does not "fix" the omission.

---

### TICKET-ADV031 — Javadoc on all public domain classes

**Goal:** Document the public contract of every domain class and public method so that a reader of the Javadoc could re-implement against it without reading the code.

**What**
- Class-level Javadoc in `WHAT / HOW / WHY` style on every public type in `model/` and `exception/`, plus `@param`/`@return`/`@throws` on every public method — including unchecked exceptions like `NullPointerException`/`IllegalStateException` thrown by Builder `build()`.

**Why**
- `@throws` on unchecked exceptions is part of the contract a caller reads off the API site; this is the reference doc Day 3-10 teammates will browse via `apidocs/index.html` instead of grepping source.

**Observe**
- `./mvnw -pl backend javadoc:javadoc` exits cleanly with zero `[WARNING]` lines; no vacuous `@param tradeRef the tradeRef` entries — every component sentence adds information beyond the field name.

**Done when:**
- Every public class in `com.dbtraining.reconx.model` has a class-level Javadoc explaining its purpose.
- Every public method has Javadoc with `@param`, `@return`, and `@throws` as appropriate.
- No vacuous Javadoc (`@param tradeRef the tradeRef`) — the contract sentence after the field name must add information.

<details>
<summary>Hint 1 — gentle direction</summary>

Good Javadoc reads like a function spec. The name tells you *what*; the doc tells you *why and what it promises*. If the doc only repeats the name, you have added noise, not value.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at the Javadoc tools' recognised tags: `@param`, `@return`, `@throws`, `@see`, `{@link ...}`, `{@code ...}`. Document the public surface only — private helpers can rely on their names. Always document the exceptions a method can throw; they are part of the contract.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

For `EquityTrade.Builder.build()`: a one-line summary, a longer paragraph explaining the build-then-validate behaviour, `@return` describing the post-conditions (never null, all invariants hold), and two `@throws` clauses — one for `NullPointerException` listing the required fields, one for `IllegalStateException` listing the invariants. The same shape applies across every public builder method.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a class-level Javadoc on every public type in `model/` and `exception/` — `WHAT/HOW/WHY` style, the same shape the trainer copy uses.
2. Document every public method with `@param`, `@return`, `@throws` as appropriate.
3. Skip Javadoc on private helpers (and on builder setters whose names are self-explanatory) — focus the work on the public surface.
4. `@throws` clauses are part of the contract — list them even for unchecked exceptions.
5. Run `./mvnw javadoc:javadoc` and treat any warning as a fail.
6. Spot-check by reading just the Javadoc — could a stranger re-implement the class from it alone?

**Reference solution** (`TradeType.java` class-level Javadoc):

```java
/**
 * ============================================================================
 * Sealed interface TradeType
 *
 * WHAT:    Sealed root of the trade hierarchy. Only the four named
 *          permitted classes can implement it. Any new asset class needs an
 *          explicit code change here — by design.
 * HOW:     `sealed ... permits ...` on Java 25.
 * WHY:     Without sealing, anyone could write their own `Trade` subclass and
 *          slip through the reconciliation engine's pattern-matching switch.
 *          Sealing turns the engine's switch into an exhaustive one — the
 *          compiler enforces that every case is handled.
 * OBSERVE: Removing `permits BondTrade` causes a compile error in
 *          ReconciliationEngine's switch expression.
 * HINT:    See Day 2 trainer guide §"Workshop 2A — sealed hierarchy" for the
 *          design discussion.
 * ============================================================================
 *
 * Comparable natural ordering (most-recent trade first)
 * equals/hashCode based on tradeRef (the natural key)
 *
 * Comparator lives on the sealed interface, so every impl shares the same
 * ordering rule — there is no per-class compareTo override to forget to
 * update when adding a new field.
 */
```

**Reference solution** (`EquityTrade.Builder.build()` Javadoc):

```java
// In EquityTrade.Builder
/**
 * Build the immutable {@link EquityTrade}, validating that every required
 * field is set and that all invariants hold.
 *
 * @return a fully-constructed, validated {@code EquityTrade} — never {@code null}.
 * @throws NullPointerException  if any required field
 *                               ({@code tradeRef}, {@code notional},
 *                               {@code tradeDate}, {@code instrumentSymbol},
 *                               {@code quantity}, {@code price}, {@code side})
 *                               was not set.
 * @throws IllegalStateException if {@code quantity} is not strictly positive,
 *                               {@code price} is negative, or
 *                               {@code instrumentSymbol} is blank.
 */
public EquityTrade build() {
    // ... see TICKET-ADV019
}
```

**Reference solution** (`Money.plus(Money)` Javadoc):

```java
/** Add another Money of the same currency. Throws on currency mismatch. */
public Money plus(Money other) { ... }
```

</details>

**▶ Verify the artifact — TICKET-ADV031 Javadoc**

After this ticket, the Javadoc tool produces a complete site for `com.dbtraining.reconx.model` and `com.dbtraining.reconx.exception` with no warnings; the rendered HTML reads like a spec, not a code echo.

```bash
# from project root
./mvnw -pl backend javadoc:javadoc
open backend/target/site/apidocs/index.html
```

**Verify the artifact contains:**

- [ ] Every public class in `model/` and `exception/` has a class-level Javadoc block (the `WHAT / HOW / WHY` shape used in the trainer copy).
- [ ] Every public method has `@param`, `@return`, `@throws` as appropriate — including unchecked exceptions where they are part of the contract (`requireNonNull`, `IllegalStateException` in builders).
- [ ] `./mvnw -pl backend javadoc:javadoc` exits cleanly with no `[WARNING]` lines.
- [ ] No vacuous `@param tradeRef the tradeRef` style entries — the rendered HTML adds information beyond the name.
- [ ] Private helpers and self-explanatory builder setters are *not* documented — the focus is the public surface.

---

### TICKET-ADV032 — PR review with two approvals

**Goal:** Open a pull request containing the day's work and obtain two reviewers' approval before merging into the team branch.

**What**
- A pushed feature branch (`feature/day2-trade-model`), a PR opened via `gh pr create` against the team integration branch carrying the eleven-item reviewer checklist inline, and two distinct teammate approvals before the merge button is clicked.

**Why**
- Two pairs of eyes today catch the contract drift that would otherwise become a Day-4 fire — Day 3's parallel reconciliation engine builds directly on this model, so a leaky type today costs hours tomorrow.

**Observe**
- The PR shows two green approvals from distinct reviewers, every checklist box ticked (or pointing to a request-changes comment with `file:line`), and `cd backend && ./mvnw test -Dtest=EquityTradeTest,TradeRefTest,MoneyTest` exits 0 on the merged branch.

**Done when:**
- The PR description summarises the scope (Workshops 2A, 2B, 2C) and links to this student guide.
- Two teammates have approved the PR, each having checked the trainer's PR-review checklist (sealed leaves are `final`, no public constructors, `BigDecimal` everywhere, `Currency` not `String`, equality keyed on `tradeRef`, ordering consistent with equality, no PII in `toString`, JSR-380 on the DTO only, full Javadoc, exceptions extend `ReconException`, factory has no side effects).
- The PR merges cleanly into the team branch; the smoke-test command in the end-of-day checklist passes.

<details>
<summary>Hint 1 — gentle direction</summary>

This is the moment two pairs of eyes confirm that the model contract is right. It is process, not code — but it is the difference between today's wobbles being caught today and them becoming Day-4 fires. Treat the checklist as a real artifact, not a formality.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Keep the PR description short and scannable: scope, files changed, the trainer's checklist as a markdown checkbox list inline. Reviewers tick boxes against the checklist as they read; if a box cannot be ticked, that is a request-changes comment with a specific file and line.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

PR title: short, action-led, e.g. "Day 2 — TradeType sealed hierarchy, factory, value objects, exception ladder". Body: one-paragraph scope, bulleted list of new files, the eleven-item review checklist copy-pasted from the trainer guide, a smoke-test command, and a link back to this student guide. Two approvals before the merge button is clicked.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Push your branch: `git push -u origin feature/day2-trade-model`.
2. Open a PR against the team integration branch via `gh pr create` or the GitHub UI.
3. Paste the template below as the PR body — scope paragraph, files-changed list, reviewer checklist, smoke-test command.
4. Request two reviewers and ping them in chat with a one-line summary.
5. Reviewers tick boxes inline as they read; any unticked box is a request-changes comment with a specific file:line.
6. After both approvals, merge into the team branch and verify the smoke-test command is still green on `main`.

**Reference solution** (PR description template):

```markdown
## Day 2 — TradeType sealed hierarchy, factory, value objects, exception ladder

Implements TICKET-ADV018 through TICKET-ADV031.

**New files** (`backend/src/main/java/com/dbtraining/reconx/`):
- `model/TradeType.java`, `model/Side.java`
- `model/EquityTrade.java`, `model/FXTrade.java`, `model/BondTrade.java`, `model/DerivativeTrade.java`
- `model/TradeFactory.java`, `model/Money.java`, `model/TradeRef.java`, `model/ReconciliationRule.java`
- `exception/ReconException.java` + 4 concrete subtypes
- `dto/TradeRequest.java`

**Reviewer checklist:**
- [ ] `sealed interface TradeType` with explicit `permits` list
- [ ] All four leaves `final`, no public constructors, built only via Builder
- [ ] `BigDecimal` for every numeric financial field — no `double`/`float`
- [ ] `java.util.Currency` everywhere — no `String` currency codes outside Builders
- [ ] `equals`/`hashCode` keyed on `tradeRef` on every concrete trade
- [ ] `Comparable<TradeType>` ordering consistent with equality
- [ ] No PII (counterpartyId) in any `toString`
- [ ] JSR-380 annotations live on `TradeRequest` DTO only, not on entities
- [ ] Full Javadoc on the public domain surface
- [ ] All exceptions extend `ReconException`
- [ ] `TradeFactory` has no side effects, no static state

**Smoke test:** `cd backend && ./mvnw test -Dtest=EquityTradeTest,TradeRefTest,MoneyTest`

Student guide: ../student-guides/day2/README.md
```

</details>

**▶ Verify the artifact — TICKET-ADV032 PR**

After this ticket, the PR is open against the team integration branch with the reviewer checklist filled in inline, two approvals are recorded, and the smoke test is green on the merged branch.

```bash
# from project root
git push -u origin feature/day2-trade-model
gh pr create --fill
cd backend && ./mvnw test -Dtest=EquityTradeTest,TradeRefTest,MoneyTest
```

**Verify the artifact contains:**

- [ ] PR title is short and action-led (e.g. "Day 2 — TradeType sealed hierarchy, factory, value objects, exception ladder").
- [ ] PR body contains the scope paragraph, the new-files list, the eleven-item reviewer checklist, and the smoke-test command.
- [ ] Two distinct approvals are recorded on the PR; any unticked checklist box has a request-changes comment with a specific `file:line`.
- [ ] After merge, the smoke test command above exits 0 on the team integration branch.
- [ ] A link back to this student guide (`student-guides/day2/README.md`) appears in the PR body.

---

## End-of-day checklist

By 17:00 each team should be able to tick every item:

- [ ] `sealed interface TradeType` plus abstract `Trade` base class in `com.dbtraining.reconx.model`.
- [ ] Four concrete trade classes (`EquityTrade`, `FXTrade`, `BondTrade`, `DerivativeTrade`), each `final`, each constructed only via its static `Builder`, each performing build-then-validate.
- [ ] `TradeFactory.create(String, Map)` returning a typed `TradeType` or throwing `InvalidTradeException`.
- [ ] Value objects: `Money` and `TradeRef`, both records, both validated in their compact constructors.
- [ ] Exception ladder: abstract `ReconException` plus four concrete subtypes in `com.dbtraining.reconx.exception`.
- [ ] `ReconciliationRule` enum with thresholds and a `matches` method, no `double` anywhere.
- [ ] `Comparable<TradeType>` consistent with `equals`; both keyed on `tradeRef`.
- [ ] `TradeRequest` DTO with JSR-380 annotations on every field.
- [ ] PII-safe `toString` on every concrete trade.
- [ ] Javadoc on every public class and method in the domain layer.
- [ ] PR opened, two approvals, merged into the team branch.
- [ ] Smoke test green: `cd backend && ./mvnw test -Dtest=EquityTradeTest,TradeRefTest,MoneyTest`.

If anything on this list is amber, raise it at the debrief. Day 3 builds parallel reconciliation directly on top of these classes — a leaky model today is an expensive fix tomorrow.
