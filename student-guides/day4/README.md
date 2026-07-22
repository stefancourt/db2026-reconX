# Day 4 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day4/README.md](../../TrainersGuide/day4/README.md)
> **Module:** Spring Boot Modules 1 & 2 — Enterprise Setup

> ## NOTE — Read this before you start
>
> **Module layout: stay single-module if you want Day 5+ to slot in cleanly.**
>
> The Hint 4 reference solutions in this day's tickets show a **multi-module Maven reactor** (`reconx-parent` / `reconx-common` / `reconx-domain` / `reconx-repository` / `reconx-service` / `reconx-api`). That layout is a great learning exercise for understanding how a real enterprise codebase splits responsibilities — but it is **illustrative, not load-bearing**. The runnable trainer copy under `reconx-trainercopy/backend/` is deliberately **single-module** (one `pom.xml`, everything under `com.dbtraining.reconx`). The comment block at the top of `backend/pom.xml` says so explicitly.
>
> What this means for you:
>
> - **Days 5 through 10 assume single-module imports** — e.g. `com.dbtraining.reconx.controller.TradeController`, `com.dbtraining.reconx.dto.TradeRequest`, `com.dbtraining.reconx.repository.entity.Trade`. If you implement Day 4 as a multi-module reactor, every Day 5+ ticket's reference code will fail to compile against your tree (the imports will not resolve) and you will spend a chunk of Day 5 refactoring package paths.
> - **If you want to learn multi-module Maven**, treat the Day 4 Hint 4 references as a thought-exercise: read them, understand the split, write down on a sticky note what would go where, then keep your actual repo single-module. You will still meet every "Done when" criterion for ADV048 — the marker reads the *pom.xml structure and the profile wiring*, not whether you have N separate modules.
> - **If you just want to ship**, stay single-module. Day 5 and later will work without surprises. The Envers auditing, JSONB metadata, MapStruct mappers, paged Specifications, Swagger groups, HealthIndicators, JSON logging, and ProblemDetail handler all still apply — they just live under the single `backend/` module instead of being scattered across five.
>
> If you discover halfway through Day 5 that your multi-module setup is fighting you, the fastest unblock is to collapse the modules back into `backend/` (move all source under `backend/src/main/java/com/dbtraining/reconx/`, delete the child poms, and let the single parent `pom.xml` take over). It is faster than refactoring every Day 5 ticket's imports.

## What you'll build today

Today is the day your project starts looking like a real Spring Boot service. You will lay out a five-module Maven reactor (common, domain, repository, service, api), wire three Spring profiles (dev/uat/prod), and turn the trade reconciliation domain into proper JPA entities with auditing via Hibernate Envers. After lunch you build the DTO layer with records, generate type-safe mappers using MapStruct, write a paged search endpoint backed by JPA Specifications, and finish with the observability stack: Swagger UI grouped by audience, custom HealthIndicators for DB and Kafka, structured JSON logging with an MDC correlation id, and an RFC 7807 ProblemDetail error pipeline. By 16:45 a clean `./mvnw install` plus a `dev`-profile boot should produce a Swagger page listing `/v1/trades` and an `/actuator/health` reporting your own indicators.

## Day at a glance

1. Standup and Day-3 holdover unblock
2. AM Module 1 lecture: Spring Boot Enterprise Setup
3. Workshop 4A: Skeleton, profiles, entities (TICKET-ADV048 – TICKET-ADV052)
4. Lunch
5. PM Module 2 lecture: Configuration and autoconfig
6. Workshop 4B: DTOs, mappers, repos, pagination (TICKET-ADV053 – TICKET-ADV057)
7. Workshop 4C: Swagger, health, logging, errors (TICKET-ADV058 – TICKET-ADV062)
8. End-of-day debrief and Day 5 preview

## Exercises

Day 4 has **15 exercises** split across three workshop blocks. Each exercise lists a goal, a concrete "Done when" checklist you can verify yourself, and a three-step hint ladder. Open hints in order: Hint 1 nudges you in a direction, Hint 2 names the file or API you should look at, Hint 3 sketches the structure of the answer without giving you the code. If you still need the literal solution after Hint 3, flag a trainer — do not skip ahead and copy from a neighbour.

The exercises are cumulative. The reactor you build in TICKET-ADV048 carries `common → domain → repository → service → api` order all day, and the `Trade` entity from TICKET-ADV050 is what every later exercise queries, maps, audits, paginates, and reports errors against.

### Workshop 4A — Skeleton, profiles, entities

The morning block is about giving the project its shape. You produce a multi-module reactor, three profile files, and the three core JPA entities (`Trade`, `Instrument`, `Counterparty`) with auditing wired in.

---

### TICKET-ADV048 — Create the multi-module Maven project

**Goal:** Stand up a five-module Maven reactor that builds end-to-end with `./mvnw -pl backend clean install`.

**What**
- A `backend/pom.xml` with `<packaging>pom</packaging>` and a `<modules>` list of `common`, `domain`, `repository`, `service`, `api`, plus a child `pom.xml` per module that declares only `<parent>` and its own `<artifactId>`.

**Why**
- The reactor enforces the layering the rest of the week relies on — `api` depends on `service`, `service` on `repository`, `repository` on `domain`, so the Day 5 REST controllers and Day 6 Kafka consumers cannot accidentally import a JPA entity from the wrong direction.

**Observe**
- `./mvnw -pl backend clean install` from the repo root exits 0 and the reactor summary prints five `BUILD SUCCESS` lines, one per module.

**Done when:**
- A parent `pom.xml` exists at `backend/pom.xml` with `<packaging>pom</packaging>` and a `<modules>` list naming `common`, `domain`, `repository`, `service`, `api`.
- Each child module has its own `pom.xml` declaring `<parent>` (groupId, artifactId, version) but does NOT re-declare its own `<groupId>` or `<version>`.
- `./mvnw -pl backend clean install` from the repo root exits with code 0 and reports five reactor builds.

<details>
<summary>Hint 1 — gentle direction</summary>

Think about the relationship between a parent and its children in Maven. The parent declares the modules; the children inherit version, groupId, and shared dependency-management entries. If you find yourself copy-pasting the same `<groupId>` into every child, you are missing the point of inheritance. Also recall what happens if Maven tries to compile a parent like a normal jar.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The relevant Maven elements are `<packaging>`, `<modules>`, `<parent>`, and `<dependencyManagement>`. The parent should use `<packaging>pom</packaging>` and live next to a directory per child module. When you run a partial build, the `-pl :artifactId -am` flag pair tells Maven to build that module *and* the modules it depends on upstream.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Parent `pom.xml`: inherits from `spring-boot-starter-parent`, sets `<packaging>pom</packaging>`, defines properties for the Java version (21), MapStruct, and Lombok, declares five `<module>` entries, and pins versions for MapStruct and the Logstash logback encoder in `<dependencyManagement>`. Each child `pom.xml`: declares only `<parent>` plus its own `<artifactId>`, `<packaging>jar</packaging>`, and the dependencies it actually uses. The `api` module depends on `service` and pulls in `spring-boot-starter-web`, `spring-boot-starter-actuator`, and `springdoc-openapi-starter-webmvc-ui`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/pom.xml` inheriting `spring-boot-starter-parent 3.5.0 with `<packaging>pom</packaging>`.
2. Declare `<properties>` for `java.version=25`, MapStruct, hypersistence, jjwt, testcontainers versions.
3. Declare every child as a `<module>` entry (common, domain, repository, service, api) — order matters for the reactor.
4. Pin shared library versions (MapStruct processor, Logstash encoder, hypersistence-utils) in `<dependencyManagement>`.
5. In each child `pom.xml` declare only `<parent>` + own `<artifactId>` + `<packaging>jar</packaging>` + the dependencies the module actually uses.
6. From the repo root run `./mvnw -pl backend clean install` and confirm 5 reactor builds exit 0.

> **Note on trainer source code:** the trainer copy ships `backend/` as a **single runnable module** so students can `mvn spring-boot:run` against a working answer key (see the comment at the top of `reconx-trainercopy/backend/pom.xml`). The five-module layout below is the Day 4 *teaching target* and matches the trainer guide for this exercise — when you finish Day 4 you have the option to keep the single-module shape or split it as shown here.

**Reference solution** (`backend/pom.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>

    <groupId>com.dbtraining.reconx</groupId>
    <artifactId>reconx-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>25</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <mapstruct.version>1.6.2</mapstruct.version>
        <lombok.version>1.18.34</lombok.version>
    </properties>

    <modules>
        <module>common</module>
        <module>domain</module>
        <module>repository</module>
        <module>service</module>
        <module>api</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            <dependency>
                <groupId>net.logstash.logback</groupId>
                <artifactId>logstash-logback-encoder</artifactId>
                <version>8.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

Reference solution (`backend/api/pom.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.dbtraining.reconx</groupId>
        <artifactId>reconx-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>reconx-api</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.dbtraining.reconx</groupId>
            <artifactId>reconx-service</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>
    </dependencies>
</project>
```

</details>

**▶ Run the project — verify TICKET-ADV048 end-to-end**

Confirm the reactor (or single-module pom) compiles cleanly before wiring anything else on top of it.

```bash
./mvnw clean install
```

**Observe:**

- Build succeeds with `BUILD SUCCESS` and exit code 0.
- If you stayed single-module: one reactor build for `reconx-api` (or whatever you named the artifactId).
- If you split into a multi-module reactor: modules build in dependency order — common, domain, repository, service, api.
- Failure signal: a `[ERROR] Failed to execute goal` line, or `Non-resolvable parent POM` if `<relativePath/>` is wrong on a child.

---

### TICKET-ADV049 — Spring profiles: dev / uat / prod

**Goal:** Configure three Spring profiles so that the same JAR can boot against H2 in dev, Postgres in UAT, and a secrets-driven Postgres in prod.

**What**
- `application.yml` plus `application-dev.yml` (H2 in PostgreSQL mode), `application-uat.yml` (Postgres dialect), and `application-prod.yml` (Postgres dialect with `${DB_PASSWORD}` style env-var lookups and no literal credentials).

**Why**
- Same JAR, three environments — Day 10's Docker Compose demo flips the profile via `SPRING_PROFILES_ACTIVE=uat` without a rebuild, and the prod profile's fail-fast env-var lookup is what stops a stray laptop accidentally booting against the prod schema.

**Observe**
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` starts on H2; the same command with `-Dspring-boot.run.profiles=prod` and no env vars set aborts with a `Could not resolve placeholder 'DB_PASSWORD'` failure.

**Done when:**
- `application.yml` selects a default active profile of `dev` using a `SPRING_PROFILES_ACTIVE` env var fallback.
- `application-dev.yml` boots against an in-memory H2 instance in PostgreSQL compatibility mode.
- `application-uat.yml` and `application-prod.yml` use the Postgres dialect; prod has no hard-coded credentials — every credential is read from an environment variable that fails fast if unset.
- `spring.jpa.open-in-view` is `false` in the base file; `spring.liquibase.change-log` starts with the `classpath:` prefix; `spring.sql.init.mode` is `never`.

<details>
<summary>Hint 1 — gentle direction</summary>

Think in terms of "shape" vs "value". A profile selects the bundle of related settings that travel together: driver, dialect, log level, feature flags. Environment variables override individual values inside that bundle. Where would you put a hard-coded password so that the build fails if someone tries to deploy without setting it?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

You will create four files under `backend/api/src/main/resources/`: `application.yml`, `application-dev.yml`, `application-uat.yml`, `application-prod.yml`. Look up the syntax `${VAR_NAME}` (no default) versus `${VAR_NAME:fallback}`. The properties that bite people: `spring.profiles.active`, `spring.datasource.url`, `spring.jpa.database-platform`, `spring.jpa.hibernate.ddl-auto`, `spring.liquibase.change-log`, `spring.sql.init.mode`, `management.endpoints.web.exposure.include`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Base file: profile active reads `${SPRING_PROFILES_ACTIVE:dev}`; application name set; `jpa.open-in-view: false`; hibernate jdbc time zone UTC; `liquibase.change-log: classpath:db/changelog/db.changelog-master.xml`; `sql.init.mode: never`; actuator exposes health/info/metrics/prometheus; context-path `/api`. Dev: H2 URL `jdbc:h2:mem:reconx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, `H2Dialect`, `ddl-auto: validate`, h2-console enabled, debug-level logging for your package and `org.hibernate.SQL`. UAT: Postgres URL on localhost, `PostgreSQLDialect`, `ddl-auto: validate`, INFO logging. Prod: every datasource value uses `${RECONX_DB_*}` with no default; root log level WARN.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/api/src/main/resources/application.yml` with `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` plus base settings (`open-in-view: false`, Liquibase path with `classpath:` prefix, `sql.init.mode: never`).
2. Create `application-dev.yml` pointing at H2 in PostgreSQL-compat mode, with `h2.console.enabled: true` and DEBUG logging for the app package.
3. Create `application-uat.yml` with the Postgres driver, `PostgreSQLDialect`, and `${POSTGRES_*}` env vars with safe defaults.
4. Create `application-prod.yml` reading every datasource value from env vars with NO defaults (Spring will fail fast if unset) and root log WARN.
5. Boot with `-Dspring-boot.run.profiles=dev`, hit `/api/h2`, confirm the H2 console loads.

**Reference solution** (`backend/api/src/main/resources/application.yml`):

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  application:
    name: reconx-api
  jpa:
    open-in-view: false                   # default true — turn off in services
    properties:
      hibernate:
        jdbc.time_zone: UTC
        format_sql: true
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml  # MUST have classpath: prefix
  sql:
    init:
      mode: never                         # Liquibase owns the schema, not data.sql

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true

server:
  port: 8080
  servlet:
    context-path: /api
```

Reference solution (`application-dev.yml`):

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:reconx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: validate                  # Liquibase does the create — JPA only validates
  h2:
    console:
      enabled: true
      path: /h2-console
logging:
  level:
    com.dbtraining.reconx: DEBUG
    org.hibernate.SQL: DEBUG
```

Reference solution (`application-uat.yml`):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/reconx_uat
    username: reconx_uat
    password: reconx_uat
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
logging:
  level:
    com.dbtraining.reconx: INFO
```

Reference solution (`application-prod.yml`):

```yaml
spring:
  datasource:
    url: ${RECONX_DB_URL}                 # no defaults — fail fast if unset
    username: ${RECONX_DB_USER}
    password: ${RECONX_DB_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
logging:
  level:
    root: WARN
    com.dbtraining.reconx: INFO
```

</details>

**▶ Run the project — verify TICKET-ADV049 end-to-end**

Boot the app under each profile in turn and confirm the right datasource shape is wired up.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# then in another terminal, stop and retry:
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
```

**Observe:**

- Boot log line `The following 1 profile is active: "dev"` (or `"uat"`).
- Under `dev` the datasource URL in the startup banner is `jdbc:h2:mem:reconx;MODE=PostgreSQL;...`; under `uat` it switches to `jdbc:postgresql://...`.
- Failure signal: under `prod` with no `RECONX_DB_URL` env var set, startup fails fast with `Could not resolve placeholder 'RECONX_DB_URL'` — that is the fail-fast behaviour you wanted.

---

### TICKET-ADV050 — `@Entity Trade` with `@ManyToOne` + auditing

**Goal:** Define the `Trade` JPA entity with lazy-loaded relationships to `Counterparty` and `Instrument`, plus Spring Data auditing columns and Hibernate Envers tracking.

**What**
- `Trade.java` in the `domain` module annotated with `@Entity`, `@Table(name = "trades")`, `@EntityListeners(AuditingEntityListener.class)`, and `@Audited`, plus a `JpaConfig` class in `api` carrying `@EnableJpaAuditing`.

**Why**
- Every later ticket — repository queries (ADV055), MapStruct mapping (ADV054), Envers history (ADV052), Day 5 REST endpoints, Day 6 Kafka projection — reads or writes this entity, so the lazy fetch + STRING enum + hand-written equals choices set today decide whether the rest of the week N+1s or not.

**Observe**
- `./mvnw -pl backend/domain test` passes a JUnit test that saves a `Trade`, reloads it in a fresh transaction, and confirms `counterparty` is a Hibernate proxy until `.getName()` is called.

**Done when:**
- `Trade` lives in the `domain` module and is annotated with `@Entity`, `@Table(name = "trades", indexes = ...)`, `@EntityListeners(AuditingEntityListener.class)`, and `@Audited`.
- `counterparty` and `instrument` use `@ManyToOne(fetch = FetchType.LAZY, optional = false)` with `@JoinColumn`.
- `status` is `@Enumerated(EnumType.STRING)` and defaults to `TradeStatus.PENDING`.
- `createdAt` is `@CreatedDate` with `updatable = false`; `modifiedAt` is `@LastModifiedDate`.
- A `@Configuration` class in the `api` module is annotated with `@EnableJpaAuditing`.
- `equals` and `hashCode` are written by hand and rely only on `id` (you do NOT use Lombok `@Data`).

<details>
<summary>Hint 1 — gentle direction</summary>

There are three traps in a JPA entity: the equals/hashCode trap (Lombok `@Data` walks every field, including lazy collections, and triggers N+1 the first time someone puts a Trade in a `HashSet`), the fetch trap (`@ManyToOne` defaults to EAGER, which silently joins counterparty and instrument on every query), and the enum trap (default `EnumType.ORDINAL` stores enum positions as ints, so reordering the enum re-points every row in the DB). Address all three.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Annotations to research: `@EntityListeners(AuditingEntityListener.class)`, `@Audited` (from `org.hibernate.envers`), `@CreatedDate`, `@LastModifiedDate`, `@EnableJpaAuditing`. The two listener-driven mechanisms are independent — Spring Data auditing populates the timestamp fields; Envers writes the `_aud` and `revinfo` tables. They are both wired by adding the right annotation in the right place.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Fields in order: `id` (`@Id`, `@GeneratedValue(IDENTITY)`), `tradeRef` (`@Column(unique=true, length=30)`), `counterparty` (`@ManyToOne LAZY`, `@JoinColumn`), `instrument` (same shape), `quantity` and `price` (`@Column(precision=18, scale=4)`), `tradeDate` (`LocalDate`), `status` (`@Enumerated(STRING)`, default PENDING), `createdAt` (`@CreatedDate`, `updatable=false`), `modifiedAt` (`@LastModifiedDate`). Indexes on `trade_date` and `status`. `equals`: same-instance shortcut, pattern-match instanceof, compare `id` only when non-null. `hashCode`: `Objects.hash(id)`. Separate `JpaConfig` class in api carries `@EnableJpaAuditing`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `Trade` in the `domain` (or `repository.entity`) package; annotate the class with `@Entity`, `@Table(name="trades", indexes=...)`, `@EntityListeners(AuditingEntityListener.class)`, `@Audited`.
2. Declare fields in order: `id`, `tradeRef`, `counterparty`, `instrument`, `quantity`, `price`, `tradeDate`, `status`, `createdAt`, `modifiedAt`.
3. Use `@ManyToOne(fetch=LAZY, optional=false)` + `@JoinColumn` for both relations; `@Enumerated(STRING)` on `status` defaulted to `PENDING`.
4. Annotate `createdAt` with `@CreatedDate updatable=false`; `modifiedAt` with `@LastModifiedDate`.
5. Override `equals` and `hashCode` by hand using only `id` (skip Lombok `@Data`).
6. Create `JpaConfig` in the api module annotated `@Configuration` + `@EnableJpaAuditing` so the listener actually fires.
7. Boot, save a trade, confirm `created_at` and `modified_at` are populated.

**Reference solution** (`backend/domain/src/main/java/com/dbtraining/reconx/domain/Trade.java`):

```java
package com.dbtraining.reconx.domain;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trades_trade_date", columnList = "trade_date"),
    @Index(name = "idx_trades_status",     columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Audited
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_ref", nullable = false, unique = true, length = 30)
    private String tradeRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id", nullable = false)
    private Counterparty counterparty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeStatus status = TradeStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "modified_at")
    private Instant modifiedAt;

    // --- getters / setters omitted for brevity ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
```

Reference solution (`backend/api/src/main/java/com/dbtraining/reconx/config/JpaConfig.java`):

```java
package com.dbtraining.reconx.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
```

</details>

**▶ Run the project — verify TICKET-ADV050 end-to-end**

Boot under Postgres so you can inspect the actual `trades` schema generated by Liquibase + JPA validate.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
# in another terminal:
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\d trades"
```

**Observe:**

- `\d trades` lists columns matching your `@Column` annotations: `id`, `trade_ref`, `counterparty_id`, `instrument_id`, `quantity`, `price`, `trade_date`, `status`, `created_at`, `modified_at`.
- Indexes `idx_trades_trade_date` and `idx_trades_status` are listed.
- Failure signal: a `Schema-validation: missing column [created_at]` startup error means `@EnableJpaAuditing` is missing from `JpaConfig` or the Liquibase changelog has not been updated to add the audit columns.

---

### TICKET-ADV051 — `Instrument` entity with JSONB metadata

**Goal:** Map the `Instrument` entity, including a `Map<String, Object> metadata` field that stores as JSONB on Postgres and as a CLOB on H2.

**What**
- `Instrument.java` with a `Map<String, Object> metadata` field annotated `@Type(JsonBinaryType.class)` and `@Column(columnDefinition = "jsonb")`, backed by the `hypersistence-utils` dependency pinned in the parent POM.

**Why**
- Day 1's JSONB column (TICKET-ADV009) now becomes a typed Java field — the Day 9 reporting layer can store arbitrary ISIN/CUSIP metadata without a schema change, and the `MODE=PostgreSQL` H2 URL keeps the same column type working in dev.

**Observe**
- A round-trip JUnit test saves an `Instrument` with `{"isin":"GB00B16GWD56","cusip":"037833100"}` and asserts the reloaded map equals the original; under H2 the column is a CLOB, under Postgres it is `jsonb`.

**Done when:**
- `Instrument` has fields `id`, `symbol` (unique), `name`, `assetClass` (enum, STRING), `currency` (length 3), and `metadata`.
- `metadata` is annotated with `@Type(JsonBinaryType.class)` and `@Column(columnDefinition = "jsonb")`.
- The H2 datasource URL in `application-dev.yml` includes `MODE=PostgreSQL` so the column type round-trips.
- Saving and reloading an Instrument with arbitrary keys in the metadata map produces the same map.

<details>
<summary>Hint 1 — gentle direction</summary>

`@ElementCollection` sounds right for "a bag of key-value pairs" but actually produces a separate join table — that is not what we want. We want a single column holding JSONB. You will need a Hibernate user-type implementation; the standard one in the ecosystem is shipped by `hibernate-types-json` / `hypersistence-utils`.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The annotation pair is `@Type(JsonBinaryType.class)` from `io.hypersistence.utils.hibernate.type.json` and `@Column(columnDefinition = "jsonb")`. On Postgres, JSONB is a real type. On H2 in PostgreSQL mode, JSONB falls back to a CLOB but the dialect maps it sensibly so your code stays portable. Look up the `@Type` import path carefully — the older Hibernate `@Type` annotation has a different API.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Field order: id (IDENTITY), symbol (unique, length 20), name (length 200), assetClass (`@Enumerated(STRING)`, `name = "asset_class"`, length 20), currency (length 3), metadata (`Map<String, Object>` initialised to `new HashMap<>()`, annotated with `@Type(JsonBinaryType.class)` and `@Column(columnDefinition = "jsonb")`). Class-level: `@Entity`, `@Table(name = "instruments")`. No auditing required at this layer — the audit story is concentrated on `Trade`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add the `io.hypersistence:hypersistence-utils-hibernate-63` dependency to the `domain` module pom.
2. Create `Instrument` annotated `@Entity` + `@Table(name="instruments")`.
3. Declare fields: id (IDENTITY), symbol (unique len 20), name (len 200), assetClass (`@Enumerated(STRING)` len 20), currency (len 3), metadata.
4. Annotate `metadata` with `@Type(JsonBinaryType.class)` and `@Column(columnDefinition="jsonb")`; initialise to `new HashMap<>()` so it is never null.
5. Confirm `application-dev.yml` H2 URL includes `MODE=PostgreSQL` so the `jsonb` column type translates cleanly.
6. Write a quick smoke test: save an instrument with `{"isin":"...","sector":"..."}` metadata, reload, assert equality.

**Reference solution** (`backend/domain/src/main/java/com/dbtraining/reconx/domain/Instrument.java`):

```java
package com.dbtraining.reconx.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * JSONB metadata: tick size, lot size, exchange code, etc.
     * On H2 (dev profile) this stores as a CLOB; on Postgres it's true JSONB
     * and is queryable via the @> operator.
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    // --- getters / setters omitted for brevity ---
}
```

</details>

**▶ Run the project — verify TICKET-ADV051 end-to-end**

Boot under Postgres so the `jsonb` column type is honoured, then inspect the schema.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
# in another terminal:
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\d instruments"
```

**Observe:**

- The `metadata` column is listed with type `jsonb` (not `text` or `clob`).
- Columns `symbol`, `name`, `asset_class`, `currency` match the entity definition.
- Failure signal: `column "metadata" is of type bytea` means the `@Type(JsonBinaryType.class)` annotation is missing — Hibernate fell back to its default serialiser.

---

### TICKET-ADV052 — Hibernate Envers on the `Trade` entity

**Goal:** Verify that Envers writes one row per committed change to `Trade` into the `trades_aud` table, and expose two query methods on a `TradeHistoryService`.

**What**
- A `TradeHistoryService` in the `service` module with `revisionsFor(tradeId)` and `snapshotAt(tradeId, rev)` methods, both `@Transactional(readOnly = true)` and backed by Envers' `AuditReader`.

**Why**
- Envers gives the compliance officer persona from the Day 1 C4 Context diagram a tamper-evident trail without writing a single trigger — Day 8's compliance report queries this service directly, and the Day 9 audit endpoint exposes `revisionsFor`.

**Observe**
- After three updates to a `Trade`, `SELECT count(*) FROM trades_aud WHERE id = ?` returns 4 (insert + 3 updates), and `revisionsFor` returns four revision numbers each matched by a row in `revinfo`.

**Done when:**
- After three updates to a single trade, `trades_aud` contains four rows for that trade id (one insert + three updates) and `revinfo` has matching `REV` ids with timestamps.
- `TradeHistoryService.revisionsFor(tradeId)` returns the list of revision numbers.
- `TradeHistoryService.snapshotAt(tradeId, rev)` returns the entity state at that revision.
- Both methods are `@Transactional(readOnly = true)`.

<details>
<summary>Hint 1 — gentle direction</summary>

Envers piggybacks on Hibernate event listeners that Spring Boot already wires when `hibernate-envers` is on the classpath. If your audit table stays empty, the issue is almost always that the listener never fired — usually because the entity-listener annotation is missing or `@EnableJpaAuditing` is missing on a config class. Note the distinction: the Spring Data audit listener and Envers are independent, but they share the same plumbing.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The class you need to inject is `jakarta.persistence.EntityManager`. The factory you need is `org.hibernate.envers.AuditReaderFactory`. The two methods on `AuditReader` are `getRevisions(Class<?>, Object)` and `find(Class<?>, Object, Number)`. Envers creates two tables automatically: `trades_aud` (entity state per revision plus `REV` and `REVTYPE` where 0=insert/1=update/2=delete) and `revinfo` (timestamp per revision).

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`TradeHistoryService` in the service module: one private final `EntityManager em` field, constructor-injected. Method `revisionsFor(Long tradeId)` returns `List<Number>` — get an `AuditReader` from `AuditReaderFactory.get(em)`, call `getRevisions(Trade.class, tradeId)`. Method `snapshotAt(Long tradeId, Number revision)` returns `Trade` — same reader, call `find(Trade.class, tradeId, revision)`. Both methods carry `@Transactional(readOnly = true)`. No additional config beyond `hibernate-envers` on the classpath and the `@Audited` annotation already on `Trade`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Confirm `hibernate-envers` is on the classpath and `@Audited` is on `Trade` (from ADV050).
2. Boot once under dev so Envers auto-creates `trades_aud` and `revinfo` tables.
3. In the `service` module create `TradeHistoryService` with a constructor-injected `EntityManager`.
4. Implement `revisionsFor(Long tradeId)` using `AuditReaderFactory.get(em).getRevisions(Trade.class, tradeId)`.
5. Implement `snapshotAt(Long tradeId, Number rev)` using `reader.find(Trade.class, tradeId, rev)`.
6. Annotate both methods `@Transactional(readOnly = true)`.
7. Update a trade three times in a test; assert `trades_aud` has four rows and `revisionsFor` returns four revision numbers.

**Reference solution** (`backend/service/src/main/java/com/dbtraining/reconx/service/TradeHistoryService.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.domain.Trade;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TradeHistoryService {

    private final EntityManager em;

    public TradeHistoryService(EntityManager em) { this.em = em; }

    @Transactional(readOnly = true)
    public List<Number> revisionsFor(Long tradeId) {
        AuditReader reader = AuditReaderFactory.get(em);
        return reader.getRevisions(Trade.class, tradeId);
    }

    @Transactional(readOnly = true)
    public Trade snapshotAt(Long tradeId, Number revision) {
        AuditReader reader = AuditReaderFactory.get(em);
        return reader.find(Trade.class, tradeId, revision);
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV052 end-to-end**

Boot the app and confirm Envers auto-created its audit tables, then modify a trade and watch a revision row appear.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
# in another terminal:
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\dt *_aud"
psql -h localhost -p 5432 -U reconx_uat -d reconx_uat -c "\dt revinfo"
```

**Observe:**

- `trades_aud` and `revinfo` tables are listed.
- After updating a trade three times (via your tests or curl), `SELECT id, rev, revtype FROM trades_aud WHERE id = <yourTradeId>` returns four rows: one with `revtype=0` (insert) and three with `revtype=1` (update).
- Failure signal: empty `trades_aud` means `@Audited` is missing from `Trade`, or `hibernate-envers` is not on the classpath.

---

### Workshop 4B — DTOs, mappers, repos, pagination

The afternoon block is about not leaking the JPA layer through HTTP. You build DTO records, generate type-safe mappers, write a repository with both `@Query` and Specifications, and wrap pagination in a stable JSON envelope.

---

### TICKET-ADV053 — DTO layer and `PagedResponse<T>`

**Goal:** Define `TradeRequest`, `TradeResponse`, and a generic `PagedResponse<T>` so controllers never leak JPA entities to the wire.

**What**
- Three Java `record` types — `TradeRequest` (validation-annotated input), `TradeResponse` (flat counterparty/instrument fields), and `PagedResponse<T>(items, page, size, totalElements, totalPages)` with a static `of(Page<E>, Function<E,T>)` factory.

**Why**
- Decoupling the wire shape from the JPA entity is what keeps `open-in-view=false` (ADV049) viable — without this DTO envelope, the Day 5 REST controller would serialise lazy collections and explode, and the Day 7 React frontend would have to parse Spring's `pageable` blob.

**Observe**
- `./mvnw -pl backend/common test` passes a test that constructs a `Page<Trade>` of two rows and verifies `PagedResponse.of(page, t -> t.getTradeRef())` produces `items=[ref1, ref2]` with `totalElements=2`.

**Done when:**
- `TradeRequest` is a `record` carrying validation annotations: `@NotBlank`, `@NotNull`, `@DecimalMin`, `@PastOrPresent`.
- `TradeResponse` is a `record` with flat counterparty/instrument fields (`counterpartyId`, `counterpartyName`, `instrumentId`, `instrumentSymbol`) — no nested objects.
- `PagedResponse<T>` is a `record` with `items`, `page`, `size`, `totalElements`, `totalPages`.
- `PagedResponse` exposes a static factory `of(Page<E>, Function<E, T> mapper)` that maps the page's content using the supplied function.

<details>
<summary>Hint 1 — gentle direction</summary>

Two reasons not to ship the Spring `Page<Trade>` directly: (a) it serialises with a `pageable` nested object the UI team will hate, and (b) the entity carries lazy collections that blow up at serialise time because `open-in-view` is off. The fix for both is the same — define your own DTO envelope and project into it inside the transactional service method.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Validation annotations live in `jakarta.validation.constraints`. The Spring page type is `org.springframework.data.domain.Page`. Records give you immutability, a compact constructor, and Jackson-friendly serialisation for free. For the static factory, look up `java.util.function.Function` and `Stream.map(...).toList()`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`TradeRequest` fields: `tradeRef` (`@NotBlank`), `counterpartyId` (`@NotNull`), `instrumentId` (`@NotNull`), `quantity` (`@NotNull @DecimalMin(value = "0.0", inclusive = false)`), `price` (same), `tradeDate` (`@NotNull @PastOrPresent`). `TradeResponse` fields, in this order: `id`, `tradeRef`, `counterpartyId`, `counterpartyName`, `instrumentId`, `instrumentSymbol`, `quantity`, `price`, `tradeDate`, `status` (as String), `createdAt`, `modifiedAt`. `PagedResponse<T>` has the five fields named above; the static `of` calls `page.getContent().stream().map(mapper).toList()` and reads `getNumber`, `getSize`, `getTotalElements`, `getTotalPages` off the page.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `service` (or `common`) module add a `dto` package.
2. Declare `TradeRequest` as a `record` with the six wire-form fields and JSR-380 constraints.
3. Declare `TradeResponse` as a `record` with flat counterparty/instrument fields (no nesting) and status as `String`.
4. Declare `PagedResponse<T>` as a generic `record` carrying `items, page, size, totalElements, totalPages`.
5. Add the static factory `of(Page<E>, Function<E,T>)` that maps the page content through the supplied function.
6. Confirm no entity types are referenced from any record (DTOs must not leak JPA).

**Reference solution** (`backend/api/src/main/java/com/dbtraining/reconx/dto/TradeRequest.java`):

```java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRequest(
    @NotBlank String tradeRef,
    @NotNull Long counterpartyId,
    @NotNull Long instrumentId,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
    @NotNull @PastOrPresent LocalDate tradeDate
) {}
```

Reference solution (`backend/api/src/main/java/com/dbtraining/reconx/dto/TradeResponse.java`):

```java
package com.dbtraining.reconx.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TradeResponse(
    Long id,
    String tradeRef,
    Long counterpartyId,
    String counterpartyName,
    Long instrumentId,
    String instrumentSymbol,
    BigDecimal quantity,
    BigDecimal price,
    LocalDate tradeDate,
    String status,
    Instant createdAt,
    Instant modifiedAt
) {}
```

Reference solution (`backend/api/src/main/java/com/dbtraining/reconx/dto/PagedResponse.java`):

```java
package com.dbtraining.reconx.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV053 end-to-end**

Records are compile-time constructs — a `./mvnw compile` is enough to prove they are well-formed.

```bash
./mvnw clean compile
```

**Observe:**

- Compilation succeeds. The generated class files for each record contain auto-generated `equals`, `hashCode`, `toString`, and accessor methods (one per component).
- `PagedResponse.of(...)` resolves against `java.util.function.Function` and `org.springframework.data.domain.Page` — no entity types appear in any record signature.
- Failure signal: a `cannot find symbol: class Trade` compile error inside a DTO means an entity leaked into a record — DTOs must reference only primitives, JDK types, and other DTOs.

---

### TICKET-ADV054 — MapStruct mapper

**Goal:** Generate a Spring-managed `TradeMapper` bean that maps `Trade` to/from `TradeRequest`/`TradeResponse` at compile time.

**What**
- `TradeMapper.java` interface annotated `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)` plus a `maven-compiler-plugin` block in `backend/api/pom.xml` declaring `mapstruct-processor` in `<annotationProcessorPaths>`.

**Why**
- Compile-time mapping means the Day 5 REST controller (`TradeController`) gets a zero-reflection `Trade <-> TradeResponse` translation, and `ReportingPolicy.ERROR` fails the build the moment someone adds a field to `TradeResponse` and forgets to map it.

**Observe**
- `./mvnw -pl backend/api compile` generates `target/generated-sources/annotations/.../TradeMapperImpl.java` containing a `@Component`-annotated class with explicit getter/setter calls and no reflection.

**Done when:**
- `TradeMapper` is an interface annotated with `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`.
- `toResponse(Trade)` maps `counterparty.id` to `counterpartyId`, `counterparty.name` to `counterpartyName`, `instrument.id` to `instrumentId`, `instrument.symbol` to `instrumentSymbol`, and converts the enum status to a String via a `@Named` helper.
- `toEntity(TradeRequest)` ignores fields the service will fill in: `id`, `counterparty`, `instrument`, `status`, `createdAt`, `modifiedAt`.
- The `maven-compiler-plugin` is configured with `mapstruct-processor` in `annotationProcessorPaths`.
- After `./mvnw -pl backend/api compile`, the generated class `TradeMapperImpl` appears under `target/generated-sources/annotations/...`.

<details>
<summary>Hint 1 — gentle direction</summary>

Two classic MapStruct failures: (a) Spring cannot find the bean — that means `componentModel = "spring"` is missing, so MapStruct generated a plain class with no `@Component`; (b) the IDE shows red because the implementation has not been generated — fix by running compile and, in IntelliJ, enabling annotation processing. Also think about which direction needs `ignore`: id is populated on the response, but ignored on the request side.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Annotations to research: `@Mapper`, `@Mapping(source = "...", target = "...")`, `@Named`, `ReportingPolicy.ERROR`. The Maven plugin configuration involves `<annotationProcessorPaths>` inside `<configuration>` of `maven-compiler-plugin`. The processor coordinate is `org.mapstruct:mapstruct-processor` and the version comes from the parent property you defined in TICKET-ADV048.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Interface `TradeMapper` in `backend/api/.../dto/`: class-level `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`. Method `TradeResponse toResponse(Trade trade)` carries four `@Mapping(source = "counterparty.xxx"/"instrument.xxx", target = "...Id"/"...Name"/"...Symbol")` plus a fifth `@Mapping(source = "status", target = "status", qualifiedByName = "statusToString")`. Method `Trade toEntity(TradeRequest req)` carries six `@Mapping(target = "...", ignore = true)` lines for id, counterparty, instrument, status, createdAt, modifiedAt. Helper method `@Named("statusToString") static String statusToString(Enum<?> status)` returns null when status is null and `status.name()` otherwise.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `service` (or api) module's pom add the `mapstruct` dependency and configure `maven-compiler-plugin` with `mapstruct-processor` in `<annotationProcessorPaths>`.
2. Create the `TradeMapper` interface annotated `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`.
3. Add `toResponse(Trade)` with `@Mapping` lines that flatten `counterparty.id/name` and `instrument.id/symbol` and call the named helper for `status`.
4. Add `toEntity(TradeRequest)` and `@Mapping(target=..., ignore=true)` for each field the service will fill in: `id, counterparty, instrument, status, createdAt, modifiedAt`.
5. Add the `@Named("statusToString")` helper that null-checks and calls `.name()`.
6. Run `./mvnw -pl backend/api compile` and confirm `TradeMapperImpl` appears under `target/generated-sources/annotations`.

**Reference solution** (`backend/api/src/main/java/com/dbtraining/reconx/dto/TradeMapper.java`):

```java
package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.domain.Trade;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TradeMapper {

    @Mapping(source = "counterparty.id",       target = "counterpartyId")
    @Mapping(source = "counterparty.name",     target = "counterpartyName")
    @Mapping(source = "instrument.id",         target = "instrumentId")
    @Mapping(source = "instrument.symbol",     target = "instrumentSymbol")
    @Mapping(source = "status",                target = "status",
             qualifiedByName = "statusToString")
    TradeResponse toResponse(Trade trade);

    @Mapping(target = "id",            ignore = true)
    @Mapping(target = "counterparty",  ignore = true)   // wired by service from id
    @Mapping(target = "instrument",    ignore = true)
    @Mapping(target = "status",        ignore = true)   // defaulted to PENDING
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "modifiedAt",    ignore = true)
    Trade toEntity(TradeRequest req);

    @Named("statusToString")
    static String statusToString(Enum<?> status) {
        return status == null ? null : status.name();
    }
}
```

Add MapStruct's annotation processor in the `api` module (`backend/api/pom.xml`):

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct-processor</artifactId>
            <version>${mapstruct.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

</details>

---

**▶ Run the project — verify TICKET-ADV054 end-to-end**

The MapStruct annotation processor runs at compile time — confirm it produced a generated implementation class.

```bash
./mvnw clean compile
find target/generated-sources/annotations -name "TradeMapperImpl.java"
```

**Observe:**

- `target/generated-sources/annotations/com/dbtraining/reconx/dto/TradeMapperImpl.java` exists.
- Opening that file shows the mapping logic: `target.setCounterpartyId(trade.getCounterparty().getId())` and similar lines, plus a `@Component` annotation so Spring picks it up.
- Failure signal: a compile error `Unmapped target property: "xxx"` means a field in `TradeResponse` is not covered by any `@Mapping` — the `unmappedTargetPolicy = ERROR` is doing its job; add the missing mapping.

---

### TICKET-ADV055 — `TradeRepository` with `@Query`

**Goal:** Define a Spring Data JPA repository that exposes a `findByTradeRef` derived query and a `findByFilters` JPQL query supporting optional date range, status, and counterparty filters.

**What**
- `TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade>` with a derived `findByTradeRef(String)` returning `Optional<Trade>` and a `@Query` JPQL `findByFilters(...)` using `(:param IS NULL OR ...)` for optional filters.

**Why**
- This is the data-access surface the Day 5 REST list endpoint (ADV057) and the Day 6 Kafka consumer dedupe lookup both call — `findByTradeRef` is also what the Day 8 idempotency check uses to spot duplicate counterparty submissions.

**Observe**
- `./mvnw -pl backend/repository test` passes a smoke test that calls `findByFilters(from, to, null, null, PageRequest.of(0, 10))` against the Day 1 seed and returns the trades inside the date range.

**Done when:**
- `TradeRepository` extends both `JpaRepository<Trade, Long>` and `JpaSpecificationExecutor<Trade>`.
- `findByTradeRef(String)` returns `Optional<Trade>`.
- `findByFilters(LocalDate from, LocalDate to, TradeStatus status, Long counterpartyId, Pageable)` returns `Page<Trade>` and is implemented with a `@Query` JPQL text block using the `(:param IS NULL OR ...)` idiom for the optional filters.
- A simple smoke test calling `findByFilters(from, to, null, null, PageRequest.of(0, 10))` returns the seed trades inside the date range.

<details>
<summary>Hint 1 — gentle direction</summary>

Two methods, two styles. The derived query (Spring parses the method name) is the right tool for "find by exactly one field". The `@Query` annotation is the right tool when you need multiple parameters with optional behaviour. For optional filters the standard trick is `(:param IS NULL OR field = :param)` — it works but degrades to a full scan on big tables, which is why the next exercise switches to Specifications.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports you need: `JpaRepository`, `JpaSpecificationExecutor`, `Page`, `Pageable`, `@Query`, `@Param`. JPQL — not SQL — refers to entity field names, not column names (`t.tradeDate`, not `t.trade_date`; `t.counterparty.id`, not a join). Java 25 text blocks (triple-quoted strings) keep multi-line JPQL readable.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Interface signature: `public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade>`. Method 1: `Optional<Trade> findByTradeRef(String tradeRef);` — no body, Spring derives the implementation. Method 2: `@Query` with a JPQL text block selecting from `Trade t` with `WHERE t.tradeDate BETWEEN :from AND :to AND (:status IS NULL OR t.status = :status) AND (:counterpartyId IS NULL OR t.counterparty.id = :counterpartyId)`. Parameters are annotated `@Param("from")`, etc. Final parameter is `Pageable pageable`. Return type `Page<Trade>`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `repository` module create `TradeRepository` interface extending both `JpaRepository<Trade, Long>` and `JpaSpecificationExecutor<Trade>`.
2. Declare `Optional<Trade> findByTradeRef(String tradeRef);` — Spring derives the SQL.
3. Declare `findByFilters(...)` with a `@Query` JPQL text block using the `(:param IS NULL OR ...)` idiom for each optional filter.
4. Annotate each parameter with `@Param("...")`; last parameter is `Pageable pageable`; return `Page<Trade>`.
5. Write a smoke test: call `findByFilters(from, to, null, null, PageRequest.of(0, 10))` and assert the seed trades within the date range are returned.

**Reference solution** (`backend/repository/src/main/java/com/dbtraining/reconx/repository/TradeRepository.java`):

```java
package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.domain.TradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface TradeRepository
        extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    Optional<Trade> findByTradeRef(String tradeRef);

    @Query("""
        SELECT t FROM Trade t
        WHERE t.tradeDate BETWEEN :from AND :to
          AND (:status IS NULL OR t.status = :status)
          AND (:counterpartyId IS NULL OR t.counterparty.id = :counterpartyId)
        """)
    Page<Trade> findByFilters(
        @Param("from")           LocalDate from,
        @Param("to")             LocalDate to,
        @Param("status")         TradeStatus status,
        @Param("counterpartyId") Long counterpartyId,
        Pageable pageable
    );
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV055 end-to-end**

Boot the app and hit the list endpoint with a filter so you can see the JPQL render and the SQL it produces in the logs.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl "http://localhost:8080/api/v1/trades?counterpartyId=1&status=PENDING"
```

**Observe:**

- The SQL log line (DEBUG level under `dev`) shows a `WHERE` clause referencing `trade_date BETWEEN ? AND ? AND (? IS NULL OR status = ?) AND (? IS NULL OR counterparty_id = ?)`.
- The response payload contains only trades matching `counterpartyId=1` and `status=PENDING`.
- Failure signal: `Parameter with that name [from] did not exist` means a `@Param` annotation is missing on a method parameter, or the name does not match the placeholder in the JPQL.

---

### TICKET-ADV056 — Specification-based dynamic queries

**Goal:** Replace the `(:param IS NULL OR ...)` style with composable JPA Specifications so each filter is independently testable.

**What**
- `TradeSpecification` final class (private constructor) exposing four static `Specification<Trade>` factories: `tradeDateBetween`, `hasStatus`, `forCounterparty`, `refLike`, each returning `cb.conjunction()` when its argument is null/blank.

**Why**
- Specifications replace ADV055's `IS NULL OR` predicates with composable, independently testable units — the Day 5 search endpoint and the Day 9 reporting filters both chain them via `Specification.where(...).and(...)` instead of growing one giant JPQL string.

**Observe**
- A unit test passing only a date range produces SQL with a single `BETWEEN` clause; passing all four filters produces SQL with `BETWEEN ... AND status = ? AND counterparty_id = ? AND trade_ref LIKE ?` and no leftover `1=1` no-ops in the WHERE.

**Done when:**
- A final class `TradeSpecification` with a private constructor exposes four static factories: `tradeDateBetween(from, to)`, `hasStatus(status)`, `forCounterparty(counterpartyId)`, `refLike(pattern)`.
- Each factory returns `Specification<Trade>` and short-circuits to `cb.conjunction()` (a no-op `WHERE true`) when its argument is null/blank.
- A query service composes the specs with `Specification.where(...).and(...).and(...)` and calls `tradeRepository.findAll(spec, pageable)`.
- A unit test exercising "only date range supplied" and "all four supplied" both return the right rows.

<details>
<summary>Hint 1 — gentle direction</summary>

A Specification is just a function from `(Root, CriteriaQuery, CriteriaBuilder)` to `Predicate`. When the filter argument is null you want the predicate to be a no-op so it can still be chained — that is `cb.conjunction()`. Think about composition: each spec is independently testable, and you build the final query by chaining `where().and().and()`.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports: `org.springframework.data.jpa.domain.Specification`, `jakarta.persistence.criteria.*`. The CriteriaBuilder methods you need: `cb.conjunction()`, `cb.equal(...)`, `cb.between(...)`, `cb.lessThanOrEqualTo(...)`, `cb.greaterThanOrEqualTo(...)`, `cb.like(...)`. To navigate a relationship: `root.get("counterparty").get("id")`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Final class with private constructor. `tradeDateBetween`: if both null return `cb.conjunction()`; if `from` null use `lessThanOrEqualTo(root.get("tradeDate"), to)`; if `to` null use `greaterThanOrEqualTo`; otherwise `between(root.get("tradeDate"), from, to)`. `hasStatus`: null returns `conjunction`; else `cb.equal(root.get("status"), status)`. `forCounterparty`: null returns `conjunction`; else `cb.equal(root.get("counterparty").get("id"), counterpartyId)`. `refLike`: null or blank returns `conjunction`; else `cb.like(root.get("tradeRef"), pattern + "%")`. Composition site: `Specification.where(tradeDateBetween(from, to)).and(hasStatus(status)).and(forCounterparty(cpId)).and(refLike(refPattern))`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `repository` module create a `final` class `TradeSpecification` with a private no-arg constructor.
2. Add `tradeDateBetween(from, to)` — short-circuit to `cb.conjunction()` when both null; pick `lessThanOrEqualTo`/`greaterThanOrEqualTo`/`between` based on which side is null.
3. Add `hasStatus(status)` — null returns `cb.conjunction()`; else `cb.equal(root.get("status"), status)`.
4. Add `forCounterparty(counterpartyId)` — null returns `cb.conjunction()`; else `cb.equal(root.get("counterparty").get("id"), counterpartyId)`.
5. Add `refLike(pattern)` — null/blank returns `cb.conjunction()`; else `cb.like(root.get("tradeRef"), pattern + "%")`.
6. In the query service compose them with `Specification.where(...).and(...).and(...)` and call `tradeRepository.findAll(spec, pageable)`.
7. Write tests for "only date range" and "all four supplied" — both must return the right rows.

**Reference solution** (`backend/repository/src/main/java/com/dbtraining/reconx/repository/TradeSpecification.java`):

```java
package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.domain.Trade;
import com.dbtraining.reconx.domain.TradeStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class TradeSpecification {

    private TradeSpecification() {}

    public static Specification<Trade> tradeDateBetween(LocalDate from, LocalDate to) {
        return (root, q, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from == null)               return cb.lessThanOrEqualTo(root.get("tradeDate"), to);
            if (to == null)                 return cb.greaterThanOrEqualTo(root.get("tradeDate"), from);
            return cb.between(root.get("tradeDate"), from, to);
        };
    }

    public static Specification<Trade> hasStatus(TradeStatus status) {
        return (root, q, cb) -> status == null
            ? cb.conjunction()
            : cb.equal(root.get("status"), status);
    }

    public static Specification<Trade> forCounterparty(Long counterpartyId) {
        return (root, q, cb) -> counterpartyId == null
            ? cb.conjunction()
            : cb.equal(root.get("counterparty").get("id"), counterpartyId);
    }

    public static Specification<Trade> refLike(String pattern) {
        return (root, q, cb) -> pattern == null || pattern.isBlank()
            ? cb.conjunction()
            : cb.like(root.get("tradeRef"), pattern + "%");
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV056 end-to-end**

Boot the app and hit the search endpoint with different filter combinations — the generated SQL should change shape based on which params are supplied.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl "http://localhost:8080/api/v1/trades?from=2026-01-01&to=2026-12-31"
curl "http://localhost:8080/api/v1/trades?from=2026-01-01&to=2026-12-31&status=SETTLED&counterpartyId=1"
```

**Observe:**

- First call's SQL log shows only `trade_date BETWEEN ? AND ?` in the WHERE — no spurious `status` or `counterparty_id` clauses.
- Second call's SQL log shows all three predicates joined with `AND`.
- Failure signal: every query produces the same SQL regardless of supplied filters — your specs are not short-circuiting to `cb.conjunction()` for null arguments.

---

### TICKET-ADV057 — Pagination on the list endpoint

**Goal:** Expose `GET /v1/trades` with optional filters and Spring's `Pageable` support, capped to a sane default page size.

**What**
- `TradeController` (a `@RestController` at `/v1/trades`) with a `GET` method that binds optional `from`/`to`/`status`/`counterpartyId` plus a `Pageable` annotated `@PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC)`, returning `PagedResponse<TradeResponse>`.

**Why**
- This is the first user-visible REST endpoint of the project — Day 5 builds JWT auth and the remaining `/v1` endpoints on top of this exact shape, and the `@PageableDefault` cap is what stops a hostile caller asking for `?size=1000000` and tipping over Postgres.

**Observe**
- `curl -s 'http://localhost:8080/v1/trades?page=0&size=5' | jq` returns HTTP 200 with `{items:[...], page:0, size:5, totalElements:N, totalPages:M}` and no `pageable` blob leaking through.

**Done when:**
- `TradeController` is a `@RestController` mapped at `/v1/trades`.
- `GET /v1/trades` accepts `from`, `to`, `status`, `counterpartyId` as optional request parameters, with `from`/`to` decoded using `@DateTimeFormat(iso = ISO.DATE)`.
- The `Pageable` parameter carries `@PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC)`.
- The endpoint returns `PagedResponse<TradeResponse>` built via `PagedResponse.of(page, mapper::toResponse)`.
- A request to `GET /v1/trades?page=0&size=5` returns 200 with a stable JSON envelope.

<details>
<summary>Hint 1 — gentle direction</summary>

Spring auto-binds a `Pageable` from `page`, `size`, `sort` query parameters. Without `@PageableDefault` a caller can request `?size=10000` and your database will hate you. Also remember: the controller should not return a `Page<Trade>` directly — it should map through the mapper into the `PagedResponse<TradeResponse>` envelope you built in TICKET-ADV053.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports: `Pageable`, `Sort`, `PageableDefault`, `@DateTimeFormat`, `@RequestParam`. The query service method takes `(from, to, status, counterpartyId, pageable)` and returns `Page<Trade>`. The controller calls `PagedResponse.of(page, mapper::toResponse)` — that is a method reference into your MapStruct-generated bean.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`@RestController @RequestMapping("/v1/trades") public class TradeController`. Constructor takes `TradeQueryService` and `TradeMapper`. `@GetMapping public PagedResponse<TradeResponse> list(...)`. Parameters in order: `@RequestParam(required=false) @DateTimeFormat(iso=ISO.DATE) LocalDate from`, same for `to`, `@RequestParam(required=false) TradeStatus status`, `@RequestParam(required=false) Long counterpartyId`, `@PageableDefault(size=20, sort="tradeDate", direction=Sort.Direction.DESC) Pageable pageable`. Body: call `queryService.search(...)`, return `PagedResponse.of(page, mapper::toResponse)`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `TradeController` in the `api` module annotated `@RestController` + `@RequestMapping("/v1/trades")`.
2. Constructor-inject `TradeQueryService` and `TradeMapper`.
3. Declare `@GetMapping public PagedResponse<TradeResponse> list(...)`.
4. Add the four optional `@RequestParam` filters, decoding `from`/`to` with `@DateTimeFormat(iso = ISO.DATE)`.
5. Add `@PageableDefault(size=20, sort="tradeDate", direction=Sort.Direction.DESC) Pageable pageable` last.
6. Call `service.search(...)` to get a `Page<Trade>`, return `PagedResponse.of(page, mapper::toResponse)`.
7. Curl `GET /api/v1/trades?page=0&size=5` and confirm the JSON envelope has `items, page, size, totalElements, totalPages` only.

**Reference solution** (`backend/api/src/main/java/com/dbtraining/reconx/controller/TradeController.java`):

```java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.domain.TradeStatus;
import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.service.TradeQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/trades")
public class TradeController {

    private final TradeQueryService queryService;
    private final TradeMapper mapper;

    public TradeController(TradeQueryService queryService, TradeMapper mapper) {
        this.queryService = queryService;
        this.mapper       = mapper;
    }

    @GetMapping
    public PagedResponse<TradeResponse> list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TradeStatus status,
        @RequestParam(required = false) Long counterpartyId,
        @PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC)
        Pageable pageable
    ) {
        var page = queryService.search(from, to, status, counterpartyId, pageable);
        return PagedResponse.of(page, mapper::toResponse);
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV057 end-to-end**

Boot the app and hit the paged list endpoint — the JSON envelope shape is the contract Day 5+ tickets rely on.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl -s "http://localhost:8080/api/v1/trades?page=0&size=5" | jq
```

**Observe:**

- Response is HTTP 200 with a JSON object containing exactly `items`, `page`, `size`, `totalElements`, `totalPages` — no Spring `pageable` nested object.
- `items` is an array of `TradeResponse` records (flat counterparty/instrument fields, status as a String).
- Failure signal: response contains a `pageable: { ... }` block — the controller is returning `Page<Trade>` directly instead of `PagedResponse.of(page, mapper::toResponse)`.

---

### Workshop 4C — Swagger, health, logging, errors

The final block is the observability and error-contract layer. You add Swagger UI grouped by audience, custom HealthIndicators that say what your ops team actually needs, structured JSON logs with a correlation id propagated via MDC, and a global RFC 7807 error handler.

---

### TICKET-ADV058 — Swagger / OpenAPI configuration

**Goal:** Configure springdoc-openapi so the running app exposes Swagger UI at `/api/swagger-ui.html` and groups endpoints into a `public` group and an `admin` group.

**What**
- `OpenApiConfig.java` declaring an `OpenAPI` bean (title, version, contact, `bearerAuth` HTTP/bearer/JWT security scheme) plus two `GroupedOpenApi` beans — `public` matching `/v1/trades/**` and `/v1/recon/**`, `admin` matching `/v1/admin/**` and `/actuator/**`.

**Why**
- Swagger UI is the contract handed to the Day 7 React frontend and the Day 10 demo audience — splitting `public` from `admin` early means Day 5's JWT-gated admin endpoints do not accidentally appear on the public docs page exported to external auditors.

**Observe**
- Booting under `dev`, `http://localhost:8080/api/swagger-ui.html` loads and the group dropdown switches between `public` and `admin`; `curl http://localhost:8080/api/v1/api-docs/public | jq '.paths | keys'` lists only `/v1/trades/**` and `/v1/recon/**` paths.

**Done when:**
- A `@Configuration` class `OpenApiConfig` declares an `OpenAPI` bean with title, description, version, contact, and a `bearerAuth` security scheme of type HTTP/bearer/JWT.
- Two `GroupedOpenApi` beans exist: `public` matching `/v1/trades/**` and `/v1/recon/**`; `admin` matching `/v1/admin/**` and `/actuator/**`.
- Booting under `dev`, `http://localhost:8080/api/swagger-ui.html` loads and lets you switch between the two groups.
- `GET /api/v1/api-docs/public` returns the OpenAPI JSON limited to the public group.

<details>
<summary>Hint 1 — gentle direction</summary>

springdoc autodetects your controllers; you only need to declare the API metadata and any grouping you want. Two beans do the work: an `OpenAPI` bean for the metadata (info, security scheme) and one `GroupedOpenApi` per audience for the path filters. Auditors will thank you for splitting public from admin.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports: `io.swagger.v3.oas.models.OpenAPI`, `io.swagger.v3.oas.models.info.Info`, `io.swagger.v3.oas.models.info.Contact`, `io.swagger.v3.oas.models.security.SecurityScheme`, `io.swagger.v3.oas.models.security.SecurityRequirement`, `org.springdoc.core.models.GroupedOpenApi`. The security scheme uses `type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`@Configuration public class OpenApiConfig`. Bean 1: `OpenAPI reconxOpenAPI()` — new `OpenAPI()` with `.info(new Info().title(...).description(...).version("v1.0.0").contact(new Contact().name(...).email(...)))`, `.components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(HTTP).scheme("bearer").bearerFormat("JWT")))`, `.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))`. Bean 2: `GroupedOpenApi publicApi()` — `GroupedOpenApi.builder().group("public").pathsToMatch("/v1/trades/**", "/v1/recon/**").build()`. Bean 3: `GroupedOpenApi adminApi()` — same builder pattern, group `admin`, paths `/v1/admin/**`, `/actuator/**`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `api` module create `OpenApiConfig` annotated `@Configuration`.
2. Declare an `OpenAPI` bean with `Info` (title, description, version, contact) and a `bearerAuth` security scheme of HTTP/bearer/JWT.
3. Declare a `GroupedOpenApi publicApi` with paths `/v1/trades/**` and `/v1/recon/**`.
4. Declare a `GroupedOpenApi adminApi` with paths `/v1/admin/**` and `/actuator/**`.
5. Boot under dev, hit `/api/swagger-ui.html`, switch the group dropdown between `public` and `admin`.
6. Hit `/api/v1/api-docs/public` and confirm only public endpoints appear in the JSON.

**Reference solution** (`backend/api/src/main/java/com/dbtraining/reconx/config/OpenApiConfig.java`):

```java
package com.dbtraining.reconx.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reconxOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ReconX API")
                .description("Trade reconciliation platform — DB TDI 2026")
                .version("v1.0.0")
                .contact(new Contact()
                    .name("ReconX Team")
                    .email("reconx-team@dbtraining.com")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/v1/trades/**", "/v1/recon/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/v1/admin/**", "/actuator/**")
            .build();
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV058 end-to-end**

Boot the app and open Swagger UI in a browser — flip the group dropdown between `public` and `admin`.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# then in a browser:
open http://localhost:8080/api/swagger-ui.html
# or via curl:
curl -s "http://localhost:8080/api/v1/api-docs/public" | jq '.info.title'
```

**Observe:**

- Swagger UI loads, the dropdown shows two groups (`public` and `admin`), and the `Authorize` button shows a `bearerAuth` (HTTP/bearer/JWT) scheme.
- `public` lists `/v1/trades/**` paths; `admin` lists `/v1/admin/**` and `/actuator/**` paths.
- Failure signal: 404 on `/api/swagger-ui.html` means the `springdoc-openapi-starter-webmvc-ui` dependency is missing from the api pom.

---

### TICKET-ADV059 — `DatabaseHealthIndicator`

**Goal:** Replace Boot's default DataSource health indicator with one that owns its own SQL, its own timeout, and its own details fields.

**What**
- A `@Component("reconxDatabase")` class extending `AbstractHealthIndicator`, executing `SELECT 1` against the injected `DataSource` with a 2-second query timeout and reporting `query`, `elapsedMs` details via try-with-resources.

**Why**
- Boot's default DataSource health indicator just opens a connection — it does not run SQL and does not measure latency, so it is silent during a slow-driver outage. This replacement is what the Day 10 Grafana dashboard scrapes and what the Day 8 SRE runbook keys off.

**Observe**
- `curl -s http://localhost:8080/actuator/health | jq '.components.reconxDatabase'` returns `{"status":"UP","details":{"query":"SELECT 1","elapsedMs":<n>}}` with `<n>` a small positive integer.

**Done when:**
- A `@Component("reconxDatabase")` extending `AbstractHealthIndicator` is registered.
- The indicator runs `SELECT 1` against the configured `DataSource` with a 2-second statement timeout.
- On success, the `Health.Builder` reports `up()` plus `query=SELECT 1` and `elapsedMs=<measured value>`.
- On failure, the builder reports `down(exception)` plus the query string.
- `GET /actuator/health` shows `reconxDatabase: UP` under the `components` map.

<details>
<summary>Hint 1 — gentle direction</summary>

The class to extend gives you a `Health.Builder` and handles the "down on uncaught exception" plumbing for you. You only need to fill in `doHealthCheck(Health.Builder builder)`. Take care to use try-with-resources for the connection and the statement, set a query timeout in seconds (not ms), and measure the elapsed time with `System.nanoTime()`.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports: `org.springframework.boot.actuate.health.AbstractHealthIndicator`, `org.springframework.boot.actuate.health.Health`, `javax.sql.DataSource`, `java.sql.Connection`, `java.sql.Statement`, `java.sql.ResultSet`, `java.sql.SQLException`. The `@Component("reconxDatabase")` qualifier shows up as the key in `/actuator/health`'s `components` map (the suffix "HealthIndicator" is stripped). Constructor calls `super("ReconX database health check failed")`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Class extends `AbstractHealthIndicator`, annotated `@Component("reconxDatabase")`. Static constant `Duration TIMEOUT = Duration.ofSeconds(2)`. Field `private final DataSource dataSource`, constructor-injected, super call passes the failure message. `doHealthCheck`: capture `nanoTime` start, open connection + statement in try-with-resources, call `stmt.setQueryTimeout((int) TIMEOUT.toSeconds())`, execute `SELECT 1`, call `rs.next()`, compute elapsed millis from nanos, then `builder.up().withDetail("query", "SELECT 1").withDetail("elapsedMs", elapsedMs)`. Catch `SQLException`: `builder.down(e).withDetail("query", "SELECT 1")`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `api` module create `DatabaseHealthIndicator` extending `AbstractHealthIndicator`, annotated `@Component("reconxDatabase")`.
2. Declare `static final Duration TIMEOUT = Duration.ofSeconds(2)`.
3. Constructor-inject `DataSource`, call `super("ReconX database health check failed")`.
4. In `doHealthCheck(Health.Builder)`: capture `System.nanoTime()` start, open connection + statement in try-with-resources, set `setQueryTimeout((int) TIMEOUT.toSeconds())`.
5. Execute `SELECT 1`, advance the result set, compute `elapsedMs` from nanos.
6. On success call `builder.up().withDetail("query", "SELECT 1").withDetail("elapsedMs", elapsedMs)`.
7. Catch `SQLException` and call `builder.down(e).withDetail("query", "SELECT 1")`.
8. Hit `/api/actuator/health` and confirm a `reconxDatabase` component reads UP.

**Reference solution** (`backend/api/src/main/java/com/dbtraining/reconx/observability/DatabaseHealthIndicator.java`):

```java
package com.dbtraining.reconx.observability;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;

@Component("reconxDatabase")
public class DatabaseHealthIndicator extends AbstractHealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        super("ReconX database health check failed");
        this.dataSource = dataSource;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        long start = System.nanoTime();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout((int) TIMEOUT.toSeconds());
            try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                rs.next();
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            builder.up()
                   .withDetail("query", "SELECT 1")
                   .withDetail("elapsedMs", elapsedMs);
        } catch (SQLException e) {
            builder.down(e).withDetail("query", "SELECT 1");
        }
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV059 end-to-end**

Boot the app and hit the actuator health endpoint — your custom indicator should appear under the `components` map.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl -s "http://localhost:8080/api/actuator/health" | jq '.components.reconxDatabase'
```

**Observe:**

- `reconxDatabase` is listed with `"status": "UP"` and a `details` map containing `"query": "SELECT 1"` plus an `elapsedMs` number.
- The overall `status` is `UP`.
- Failure signal: `reconxDatabase` is missing — `@Component("reconxDatabase")` is missing or the class is not in a package scanned by the Spring Boot application class.

---

### TICKET-ADV060 — `KafkaHealthIndicator`

**Goal:** Add a conditional health indicator for the Kafka cluster that only registers when a `spring.kafka.bootstrap-servers` property is set.

**What**
- A `@Component("reconxKafka")` extending `AbstractHealthIndicator` and gated by `@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")`, building a Kafka `AdminClient` with 2s request and 3s default API timeouts and reporting `clusterId`, `nodeCount` via `describeCluster()`.

**Why**
- Day 6 introduces Kafka as the trade-event spine; this indicator is what tells the Day 8 SRE runbook the broker is reachable before the consumer starts, and the `@ConditionalOnProperty` gate is what keeps the `dev` profile (no Kafka) from showing a permanent yellow `UNKNOWN` status.

**Observe**
- Under `dev`, `curl -s http://localhost:8080/actuator/health | jq '.components | keys'` does NOT include `reconxKafka`; under `uat` with `KAFKA_BOOTSTRAP_SERVERS` set, the same query lists `reconxKafka` and the component reports `UP` with `clusterId` and `nodeCount` populated.

**Done when:**
- A `@Component("reconxKafka")` extending `AbstractHealthIndicator` is registered, gated by `@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")`.
- The indicator creates a Kafka `AdminClient` with a 2-second request timeout and a 3-second default API timeout.
- On success, it calls `describeCluster()` and records `clusterId` and `nodeCount` as details.
- Under the `dev` profile (no Kafka configured), the indicator does NOT appear under `/actuator/health` components.
- Under UAT (Kafka configured), the indicator appears and reports UP.

<details>
<summary>Hint 1 — gentle direction</summary>

Two design points: (1) the indicator should disappear entirely when Kafka is not in scope, not show a permanent yellow status; (2) the AdminClient should not hang forever waiting for a broker — set request and API timeouts explicitly. The `@ConditionalOnProperty` annotation is how you wire "register this bean only when this property exists".

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports: `org.apache.kafka.clients.admin.AdminClient`, `AdminClientConfig`, `DescribeClusterResult`, `org.springframework.boot.autoconfigure.condition.ConditionalOnProperty`, `org.springframework.beans.factory.annotation.Value`. The three config keys: `BOOTSTRAP_SERVERS_CONFIG`, `REQUEST_TIMEOUT_MS_CONFIG`, `DEFAULT_API_TIMEOUT_MS_CONFIG`. The `describeCluster()` result lets you call `.clusterId().get(timeout, unit)` and `.nodes().get(timeout, unit)`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Class extends `AbstractHealthIndicator`, annotated `@Component("reconxKafka")` and `@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")`. Constructor takes `@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers`. In `doHealthCheck`: build a `Map<String, Object> cfg` with bootstrap servers + 2_000 ms request timeout + 3_000 ms default API timeout. Wrap `AdminClient.create(cfg)` in try-with-resources. Call `describeCluster()`; pull `clusterId` and `nodes().size()` with a 2-second `.get(2, TimeUnit.SECONDS)`. Builder `up().withDetail("clusterId", ...).withDetail("nodeCount", ...)`. Catch any `Exception` → `builder.down(e)`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `api` module create `KafkaHealthIndicator` extending `AbstractHealthIndicator`.
2. Annotate the class with `@Component("reconxKafka")` and `@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")` so it disappears when Kafka is not configured.
3. Constructor: `@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers`; call `super("ReconX Kafka health check failed")`.
4. In `doHealthCheck` build a config map with bootstrap servers, 2000 ms request timeout, 3000 ms default API timeout.
5. Open `AdminClient.create(cfg)` in try-with-resources, call `describeCluster()`, pull `.clusterId().get(2, SECONDS)` and `.nodes().get(2, SECONDS).size()`.
6. On success: `builder.up().withDetail("clusterId", clusterId).withDetail("nodeCount", nodeCount)`.
7. Catch `Exception` → `builder.down(e)`.
8. Under dev (no Kafka) confirm `reconxKafka` is absent; under UAT confirm it reads UP.

**Reference solution** (`backend/api/src/main/java/com/dbtraining/reconx/observability/KafkaHealthIndicator.java`):

```java
package com.dbtraining.reconx.observability;

import org.apache.kafka.clients.admin.*;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("reconxKafka")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final String bootstrapServers;

    public KafkaHealthIndicator(
        @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}")
        String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        Map<String, Object> cfg = Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,    bootstrapServers,
            AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,   2_000,
            AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3_000
        );
        try (AdminClient admin = AdminClient.create(cfg)) {
            DescribeClusterResult cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get(2, TimeUnit.SECONDS);
            int nodeCount    = cluster.nodes().get(2, TimeUnit.SECONDS).size();
            builder.up()
                   .withDetail("clusterId", clusterId)
                   .withDetail("nodeCount", nodeCount);
        } catch (Exception e) {
            builder.down(e);
        }
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV060 end-to-end**

The Kafka indicator should appear only when `spring.kafka.bootstrap-servers` is set — boot once without Kafka, then once with.

```bash
# 1) dev profile, no Kafka:
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
curl -s "http://localhost:8080/api/actuator/health" | jq '.components | keys'

# 2) bring up Kafka and boot under uat:
docker compose up -d kafka
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
curl -s "http://localhost:8080/api/actuator/health" | jq '.components.reconxKafka'
```

**Observe:**

- Under `dev`, the components keys list does NOT include `reconxKafka` — `@ConditionalOnProperty` removed the bean.
- Under `uat` with Kafka up, `reconxKafka.status` is `UP` with `clusterId` and `nodeCount` details.
- Failure signal: stopping Kafka and re-hitting the endpoint should show `reconxKafka.status: DOWN` with the exception detail. If it stays UP, the AdminClient timeouts are too generous.

---

### TICKET-ADV061 — Structured logging with MDC

**Goal:** Emit human-readable logs in `dev` and JSON logs in `uat`/`prod`, with a per-request `correlationId` (plus optional `tradeRef`) carried across the request via SLF4J's MDC.

**What**
- `backend/api/src/main/resources/logback-spring.xml` with two `<springProfile>` blocks (plain pattern for `dev`, `LogstashEncoder` JSON for `uat,prod`) plus a `@Component @Order(1) MdcFilter implements Filter` that puts `X-Correlation-Id` / `X-Trade-Ref` into MDC and clears it in `finally`.

**Why**
- Day 6's Kafka consumer, Day 8's recon engine, and Day 10's Docker Compose stack all share the same request — without a correlation id stitched through MDC, tracing a single trade across services becomes log archaeology, and the `finally` clear is what stops the id leaking into the next request on the same Tomcat thread.

**Observe**
- `curl -H 'X-Correlation-Id: foo-123' http://localhost:8080/v1/trades` under `dev` writes a log line containing `correlationId=foo-123`; under `uat` the same request emits a JSON line with `"correlationId":"foo-123"` and a `service` custom field.

**Done when:**
- `logback-spring.xml` exists in `backend/api/src/main/resources/` with two `<springProfile>` blocks: one for `dev` using a plain pattern that includes `%X{correlationId:-}` and `%X{tradeRef:-}`, and one for `uat,prod` using `net.logstash.logback.encoder.LogstashEncoder` with `includeMdc=true` and a `service` custom field.
- A `@Component @Order(1) MdcFilter implements Filter` reads `X-Correlation-Id` from the request (or generates a UUID if absent) and `X-Trade-Ref` if present.
- The filter puts both values into MDC before calling `chain.doFilter`, and calls `MDC.clear()` in a `finally`.
- A test request with `-H "X-Correlation-Id: foo-123"` produces a log line containing `foo-123` in the dev pattern.

<details>
<summary>Hint 1 — gentle direction</summary>

MDC is just a `ThreadLocal<Map<String,String>>` exposed by SLF4J. The pattern is: at the start of every request, put the keys you want to propagate; at the end of every request, clear MDC so the keys do not leak into the next request handled by the same thread. The clear MUST go in a `finally` block. Note: MDC does not survive across `@Async` without help — that is a Day 7 concern.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports: `org.slf4j.MDC`, `jakarta.servlet.Filter`, `jakarta.servlet.FilterChain`, `jakarta.servlet.http.HttpServletRequest`, `org.springframework.core.annotation.Order`. Logback pattern tokens: `%X{key:-default}` reads from MDC. The Logstash encoder's relevant config: `<includeMdc>true</includeMdc>` and `<customFields>{"service":"reconx-api"}</customFields>`. Header names you read: `X-Correlation-Id`, `X-Trade-Ref`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`logback-spring.xml`: `<configuration>` with two `<springProfile name="dev">` and `<springProfile name="uat,prod">` blocks, each defining a `STDOUT` console appender — the first with a plain `<pattern>` that includes `%X{correlationId:-} %X{tradeRef:-}`, the second with `<encoder class="net.logstash.logback.encoder.LogstashEncoder"><includeMdc>true</includeMdc><customFields>{"service":"reconx-api"}</customFields></encoder>`. Root logger references STDOUT at INFO. `MdcFilter`: `@Component @Order(1) public class MdcFilter implements Filter`, `doFilter` casts to `HttpServletRequest`, reads `X-Correlation-Id` (default to `UUID.randomUUID().toString()`) and `X-Trade-Ref` (default null), `MDC.put` both, `chain.doFilter`, `MDC.clear()` in `finally`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `net.logstash.logback:logstash-logback-encoder` to the `api` pom.
2. Create `backend/api/src/main/resources/logback-spring.xml` with two `<springProfile>` blocks (dev = plain pattern with MDC tokens; uat,prod = LogstashEncoder).
3. Create `MdcFilter` in the `api` module annotated `@Component @Order(1)` implementing `Filter`.
4. In `doFilter` cast to `HttpServletRequest`, read `X-Correlation-Id` (default to `UUID.randomUUID().toString()`).
5. Read `X-Trade-Ref` (may be null, only `MDC.put` if non-null).
6. Wrap `chain.doFilter` in try/finally; call `MDC.clear()` in the `finally`.
7. Curl with `-H "X-Correlation-Id: foo-123"` and confirm the dev log line contains `foo-123`.

**Reference solution** (`backend/api/src/main/resources/logback-spring.xml`):

```xml
<configuration>

    <springProfile name="dev">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %X{correlationId:-} %X{tradeRef:-} %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <springProfile name="uat,prod">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdc>true</includeMdc>
                <customFields>{"service":"reconx-api"}</customFields>
            </encoder>
        </appender>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>

</configuration>
```

Reference solution (`backend/api/src/main/java/com/dbtraining/reconx/observability/MdcFilter.java`):

```java
package com.dbtraining.reconx.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class MdcFilter implements Filter {

    static final String HDR_CORRELATION = "X-Correlation-Id";
    static final String HDR_TRADE_REF   = "X-Trade-Ref";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String correlationId = header(http, HDR_CORRELATION, UUID.randomUUID().toString());
        String tradeRef      = header(http, HDR_TRADE_REF, null);
        try {
            MDC.put("correlationId", correlationId);
            if (tradeRef != null) MDC.put("tradeRef", tradeRef);
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }

    private static String header(HttpServletRequest r, String name, String fallback) {
        String v = r.getHeader(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV061 end-to-end**

Boot the app, send a request with an explicit correlation id, and confirm it propagates through every log line for that request.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal:
curl -H "X-Correlation-Id: foo-123" "http://localhost:8080/api/v1/trades?page=0&size=1"
```

**Observe:**

- The dev console log lines for that request contain `foo-123` in the correlationId slot of the pattern.
- Every log line produced during the request — controller, service, repository, SQL log — carries the same `foo-123` id.
- Failure signal: log lines show an empty correlationId slot (just the `-` from `%X{correlationId:-}`) — the `MdcFilter` is not picking up the request, usually because `@Component @Order(1)` is missing or the package is outside component-scan.
- Switching to `SPRING_PROFILES_ACTIVE=uat`, the log lines become JSON objects with `mdc.correlationId` as a field.

---

### TICKET-ADV062 — Global `@RestControllerAdvice` with RFC 7807

**Goal:** Produce `application/problem+json` responses for every domain exception, validation failure, and uncaught error, using Spring's `ProblemDetail` API.

**What**
- A `@RestControllerAdvice` class `GlobalExceptionHandler` in the `api` module with `@ExceptionHandler` methods returning `ProblemDetail` for `TradeNotFoundException` (404), `DuplicateTradeRefException` (409), `ReconException` (422), `MethodArgumentNotValidException` (400), and a fall-through `Exception` handler (500).

**Why**
- RFC 7807 turns ad-hoc error JSON into a contract — the Day 7 React client parses `type`/`title`/`detail` for user-facing messages, the Day 8 recon engine reads `reconBreakId` from the 422 body, and the generic 500 handler keeps stack traces out of customer responses for the Day 10 demo.

**Observe**
- `curl -i http://localhost:8080/v1/trades/999999` returns `HTTP/1.1 404`, `Content-Type: application/problem+json`, and a body containing `"type":"https://reconx.dbtraining.com/errors/trade-not-found"`, `"title":"Trade not found"`, and a `timestamp` property.

**Done when:**
- A `@RestControllerAdvice` class `GlobalExceptionHandler` exists in the `api` module.
- `TradeNotFoundException` maps to HTTP 404 with `type=https://reconx.dbtraining.com/errors/trade-not-found`, `title=Trade not found`, and a `timestamp` property.
- `DuplicateTradeRefException` maps to HTTP 409 with `type=.../duplicate-trade-ref`.
- `ReconException` maps to HTTP 422 with a custom `reconBreakId` property.
- `MethodArgumentNotValidException` maps to HTTP 400 with all field errors joined into the `detail`.
- Any uncaught `Exception` maps to HTTP 500 with a generic safe message and is logged at error level.

<details>
<summary>Hint 1 — gentle direction</summary>

`@RestControllerAdvice` is a global advice that intercepts exceptions thrown by any controller. For each exception type you write a `@ExceptionHandler(SomeException.class)` method that returns a `ProblemDetail`. Set the status, title, and type URI; use `setProperty` for any extra fields. For unhandled exceptions you want a fall-through `@ExceptionHandler(Exception.class)` that logs the stack and returns a safe 500.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports: `org.springframework.http.ProblemDetail`, `org.springframework.http.HttpStatus`, `org.springframework.web.bind.annotation.RestControllerAdvice`, `org.springframework.web.bind.annotation.ExceptionHandler`, `org.springframework.web.bind.MethodArgumentNotValidException`, `jakarta.validation.ConstraintViolationException`, `java.net.URI`. The factory is `ProblemDetail.forStatusAndDetail(HttpStatus, String)`. Mutators: `setType(URI)`, `setTitle(String)`, `setProperty(String, Object)`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Class-level: `@RestControllerAdvice public class GlobalExceptionHandler`, private static `Logger log`. Handler for `TradeNotFoundException`: build `ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage())`, set type URI, title "Trade not found", property `timestamp=Instant.now()`, return. Handler for `DuplicateTradeRefException`: same shape, status `CONFLICT`, type `/duplicate-trade-ref`, title "Duplicate trade reference". Handler for `ReconException`: status `UNPROCESSABLE_ENTITY`, type `/recon-failure`, title "Reconciliation failure", property `reconBreakId=ex.getReconBreakId()`. Handler for `MethodArgumentNotValidException`: collect field errors with `stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "))`, return 400 with title "Validation failed". Handler for `ConstraintViolationException`: 400 with `ex.getMessage()`. Fall-through handler for `Exception`: log the stack at error, return 500 with a safe message and title "Internal server error".

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In the `api` module create `GlobalExceptionHandler` annotated `@RestControllerAdvice`; add a private static `Logger log`.
2. Add `@ExceptionHandler(TradeNotFoundException.class)` returning a 404 `ProblemDetail` with `type`, `title`, and `timestamp` property.
3. Add a handler for `DuplicateTradeRefException` returning 409 with the `/duplicate-trade-ref` type URI.
4. Add a handler for `ReconException` returning 422 with a `reconBreakId` property pulled from the exception.
5. Add a handler for `MethodArgumentNotValidException` returning 400; join field errors with `"; "`.
6. Add a fall-through `@ExceptionHandler(Exception.class)` that logs the stack at error and returns 500 with a safe message.
7. POST a bad body and confirm `Content-Type: application/problem+json` and the right HTTP status.

**Reference solution** (`backend/api/src/main/java/com/dbtraining/reconx/exception/GlobalExceptionHandler.java`):

```java
package com.dbtraining.reconx.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TradeNotFoundException.class)
    public ProblemDetail handleNotFound(TradeNotFoundException ex, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/trade-not-found"));
        pd.setTitle("Trade not found");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(DuplicateTradeRefException.class)
    public ProblemDetail handleDuplicate(DuplicateTradeRefException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/duplicate-trade-ref"));
        pd.setTitle("Duplicate trade reference");
        return pd;
    }

    @ExceptionHandler(ReconException.class)
    public ProblemDetail handleReconConflict(ReconException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://reconx.dbtraining.com/errors/recon-failure"));
        pd.setTitle("Reconciliation failure");
        pd.setProperty("reconBreakId", ex.getReconBreakId());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
        pd.setTitle("Validation failed");
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred — please contact support with the correlationId");
        pd.setTitle("Internal server error");
        return pd;
    }
}
```

</details>

---

**▶ Run the project — verify TICKET-ADV062 end-to-end**

Boot the app and POST an invalid trade body so the validation handler kicks in — confirm the response is a proper RFC 7807 ProblemDetail.

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# in another terminal — empty body, fails @NotBlank/@NotNull:
curl -i -X POST "http://localhost:8080/api/v1/trades" \
  -H "Content-Type: application/json" -d '{}'
```

**Observe:**

- Response status is HTTP 400 with header `Content-Type: application/problem+json`.
- Body is a JSON object containing `type`, `title` (`Validation failed`), `status: 400`, `detail` (semicolon-joined field errors like `tradeRef: must not be blank; counterpartyId: must not be null`), and `instance`.
- Hitting a non-existent trade id (e.g. `GET /api/v1/trades/999999`) returns HTTP 404 with `type` ending in `/trade-not-found` and a `timestamp` property.
- Failure signal: response body is Spring's default `{"timestamp": ..., "status": 400, "error": ...}` and `Content-Type: application/json` — the `@RestControllerAdvice` is not wired (wrong package, missing annotation, or component scan exclusion).

---

## End-of-day checklist

By 16:45 each of these should be true on your machine. Tick them off in order — if one fails, fix it before moving down the list.

- [ ] `./mvnw -pl backend clean install` runs the full five-module reactor and exits 0.
- [ ] `./mvnw -pl backend/api spring-boot:run -Dspring-boot.run.profiles=dev` starts the app on port 8080.
- [ ] `http://localhost:8080/api/swagger-ui.html` loads and the dropdown shows both `public` and `admin` groups.
- [ ] `GET /api/v1/trades?page=0&size=5` returns 200 with a `PagedResponse<TradeResponse>` JSON envelope (no Spring `pageable` nested object on the wire).
- [ ] `POST /api/v1/trades` with a deliberately bad body returns HTTP 400 and `Content-Type: application/problem+json`.
- [ ] `GET /api/actuator/health` shows `reconxDatabase: UP` as a component; `reconxKafka` is absent under `dev` and present under UAT.
- [ ] Dev logs show the `correlationId` token in every line; sending `-H "X-Correlation-Id: my-id"` makes that value appear instead of a generated UUID.
- [ ] After three updates to a `Trade`, querying `trades_aud` returns four rows (one INSERT, three UPDATE) and `revinfo` has matching `REV` ids with timestamps.
- [ ] No JPA entity types appear in any controller return signature — every payload is a DTO record.
- [ ] `application-prod.yml` contains no committed credentials; every secret is read from a `${RECONX_*}` env var with no default.
