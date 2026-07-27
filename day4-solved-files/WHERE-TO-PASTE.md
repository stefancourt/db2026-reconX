# Day 4 — Solved Files & How To Run

Day 4 is the persistence + web-plumbing day. You wired the JPA
`TradeSpecifications`, gave the app a proper Swagger doc, custom
health indicators for the database and Kafka, structured logging
with a per-request MDC correlation id, and RFC-7807 `ProblemDetail`
responses for every domain exception. `TradeService` also ships
here so Day 5's REST controllers can call real methods.

**How this folder works**

The real `backend/` tree ships with Day-4 methods as `TODO(TICKET-…)`
comments whose bodies do `throw new UnsupportedOperationException("…")`.
This folder contains **complete drop-in replacement files** — every
TODO is filled in and every stubbed method has a real body. You can
either:

- **Overlay** the whole `backend/` subtree in one shot (fastest), or
- **Open each file** in this folder side-by-side with the starter to
  read the diff first, then copy the solved version over.

Both flows land at the same result.

**In this file:**

1. One-line copy command.
2. Which Day-4 tickets ship as code vs starter vs config-only.
3. File-by-file map.
4. Step-by-step run guide (JDK 21 + H2, no Docker needed).
5. What success looks like + troubleshooting.

---

## Quick start

Paths are relative to the **project root** (contains `backend/`, `docker-compose.yml`, `day4-solved-files/`).

```bash
# From the project root — one-shot overlay:
cp -R day4-solved-files/backend/ backend/
```

---

## Scope

Day 4 has 15 tickets (ADV048–062). Seven files ship in this folder;
the rest are pom-only dependencies (Envers, springdoc) or profile
config that already ships in the starter.

| Ticket | Status | Where |
|---|---|---|
| ADV048/049 — Spring profile YAML + dev/uat/prod overrides | ✓ already in starter | `application-*.yml` |
| ADV050 — Trade JPA entity + auditing | ✓ already in starter | `Trade.java` |
| ADV051 — Spring Data repositories | ✓ already in starter | `*Repository.java` |
| ADV052 — Envers | ✓ dependency in starter | pom.xml |
| ADV053/054 — DTO records + mapper | ✓ already in starter | `dto/*.java` |
| ADV055 — Custom JPQL filter query | ✓ already in starter | `TradeRepository.findByFilters` |
| ADV056 — Specification-based dynamic queries | ✓ in this folder | `TradeSpecifications.java` |
| ADV057 — Pageable / Page<T> | ✓ already wired | `TradeRepository`, `TradeController` |
| ADV058 — Swagger OpenAPI bean + bearerAuth | ✓ in this folder | `OpenApiConfig.java` |
| ADV059 — Custom `DatabaseHealthIndicator` | ✓ in this folder | `DatabaseHealthIndicator.java` |
| ADV060 — `KafkaHealthIndicator` (@ConditionalOnProperty) | ✓ in this folder | `KafkaHealthIndicator.java` |
| ADV061 — Structured logging with MDC | ✓ in this folder | `MdcFilter.java`, `logback-spring.xml` |
| ADV062 — RFC-7807 ProblemDetail | ✓ in this folder | `GlobalExceptionHandler.java` |

> **Note on `TradeService.java`:** it ships in this folder because the
> `list` method (ADV056/057) composes the Specifications and Pageable
> the rest of Day 4 needs. Its `create` / `update` / `updateStatus` /
> `softDelete` methods are the service-layer counterparts to the Day 5
> REST tickets **ADV064–ADV067** — they are here so Day 4's Swagger UI
> can exercise real endpoints and the Day 5 controllers have real
> methods to call.

---

## File-by-file map

| # | File in `day4-solved-files/` | Paste into | Tickets |
|---|-----------------------------|-----------|---------|
| 1 | `backend/…/repository/TradeSpecifications.java` | same path | ADV056 |
| 2 | `backend/…/config/OpenApiConfig.java` | same path | ADV058 |
| 3 | `backend/…/observability/DatabaseHealthIndicator.java` | same path | ADV059 |
| 4 | `backend/…/observability/KafkaHealthIndicator.java` | same path | ADV060 |
| 5 | `backend/…/observability/MdcFilter.java` | same path | ADV061 |
| 6 | `backend/src/main/resources/logback-spring.xml` | same path | ADV061 |
| 7 | `backend/…/exception/GlobalExceptionHandler.java` | same path | ADV062 |
| 8 | `backend/…/service/TradeService.java` | same path | ADV056/057 list + ADV064–067 CRUD (Day 5) |

---

## What each change does

- **`TradeSpecifications.java`** — three `Specification<Trade>` factories (`hasStatus`, `tradeDateBetween`, `hasCounterparty`). Each returns `cb.conjunction()` when its filter is `null`, so callers can compose them freely without pre-checking for nulls.
- **`OpenApiConfig.java`** — the `reconxOpenAPI()` `@Bean` sets title, version, description, contact, and registers a `bearerAuth` HTTP scheme so Swagger UI shows an "Authorize" button that accepts JWTs (green-lit for Day-5 security).
- **`DatabaseHealthIndicator.java`** — implements `doHealthCheck()` with a 2-second-timeout `SELECT 1`, records `latencyMs` as a detail. Any thrown exception bubbles up and Spring converts it to `DOWN`.
- **`KafkaHealthIndicator.java`** — `@Component("reconxKafka")` gated by `@ConditionalOnProperty("spring.kafka.bootstrap-servers")` so it disappears under `dev`. Builds an `AdminClient` with 2s request + 3s API timeouts, calls `describeCluster()`, reports `clusterId` and `nodeCount`.
- **`MdcFilter.java` + `logback-spring.xml`** — the `@Order(1)` filter puts `X-Correlation-Id` (falling back to a random UUID) and optional `X-Trade-Ref` into MDC, then `MDC.clear()` in a `finally` so ids don't leak onto the next request. The logback config has two `<springProfile>` blocks: a plain pattern with MDC tokens for `dev`, and `LogstashEncoder` JSON with `includeMdc=true` + a `service` custom field for `uat,prod`.
- **`GlobalExceptionHandler.java`** — maps each domain exception to the right HTTP status: `TradeNotFound → 404`, `DuplicateTradeRef → 409`, `InvalidTrade → 400`, `ReconciliationMismatch → 422`, plus JSR-380 `MethodArgumentNotValidException` and `ConstraintViolationException` → 400 with a readable joined message.
- **`TradeService.java`** — six methods: `create` (duplicate-check + build + save + metrics + event), `update`, `updateStatus`, `softDelete` (calls `t.softDelete()` which sets `deleted_at`), and `list` composing the three Specifications with `.where(...).and(...).and(...)`. The `list` method backs ADV056/057; the mutating methods are the service side of the Day 5 REST tickets ADV064–067.

---

## Run the project

### Before you start

1. **Java 21.** `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS.
2. **You're in the project root.**
3. **You copied the solved files.** If not: `cp -R day4-solved-files/backend/ backend/`
4. **Days 1–3 are applied** (Day 4 depends on the Day-1 audit-log fix, the Day-2 exceptions, and the Day-3 recon engine):
   ```bash
   cp -R day1-solved-files/backend/ backend/
   cp -R day2-solved-files/backend/ backend/
   cp -R day3-solved-files/backend/ backend/
   ```

### Step 1 — Compile

```bash
cd backend
./mvnw -q clean compile
echo "exit=$?"     # want exit=0
```

### Step 2 — Boot on H2

```bash
./mvnw spring-boot:run
```

Watch for `Started ReconxApplication in ~4 seconds`. In a second terminal:

```bash
curl http://localhost:8080/api/actuator/health
# → {"status":"UP","groups":["liveness","readiness"]}

curl http://localhost:8080/api/actuator/health/database
# → {"status":"UP","details":{"latencyMs":<n>}}   ← ADV059 in action

# ADV060 — reconxKafka is ABSENT under dev (no bootstrap-servers set):
curl -s http://localhost:8080/api/actuator/health | jq '.components | keys'
# → does NOT include "reconxKafka". Boot under SPRING_PROFILES_ACTIVE=uat
#   with docker compose up -d kafka to see it appear as UP.

# ADV061 — correlation id propagates from the request header to log MDC:
curl -H "X-Correlation-Id: foo-123" \
     "http://localhost:8080/api/v1/trades?page=0&size=1"
# → every log line for that request contains foo-123 in the pattern slot.

open http://localhost:8080/api/swagger-ui.html
# → Title reads "ReconX API", green "Authorize" button appears ← ADV058
```

Try a bad `POST /api/v1/trades` (e.g. missing `quantity`) — the response
should be an RFC-7807 JSON body with `title`, `status: 400`, and a
`detail` listing the field errors. That's ADV062.

Hit `Ctrl+C` when you're done.

---

## What success looks like

- `./mvnw clean compile` exits `0`.
- Boot reaches `Started ReconxApplication`.
- `/actuator/health` and `/actuator/health/database` both return `UP`.
- `/actuator/health` under `dev` has no `reconxKafka` component; under `uat` with Kafka up it appears as `UP`.
- Dev log lines contain the `correlationId` MDC slot; sending `-H "X-Correlation-Id: my-id"` makes `my-id` appear instead of a generated UUID.
- Swagger UI at `/api/swagger-ui.html` shows the customised title + Authorize button.
- A malformed POST returns a ProblemDetail JSON body with the field errors joined by `; `.

---

## If something goes wrong

- **"Cannot find symbol: SecurityRequirement / OpenAPI"** → springdoc dependency missing. Confirm `springdoc-openapi-starter-webmvc-ui` is on the classpath (it is by default in this project).
- **"Cannot find symbol: AdminClient / AdminClientConfig"** → `spring-kafka` not on the classpath. It ships in the starter's `pom.xml`; if you stripped it out, add it back.
- **"Cannot find symbol: LogstashEncoder"** → `net.logstash.logback:logstash-logback-encoder` missing. Add it to the pom; it is pinned in `dependencyManagement`.
- **`reconxKafka` shows UP under `dev`** → `spring.kafka.bootstrap-servers` is being set somewhere it shouldn't be (env var, `application-dev.yml`). `@ConditionalOnProperty` only omits the bean when the property is truly absent.
- **Log lines show an empty correlationId slot (just the `-` fallback)** → `MdcFilter` isn't being picked up. Check `@Component @Order(1)` is present and the class lives under the component-scan root (`com.dbtraining.reconx`).
- **"Cannot find symbol: TradeEvent.EventType"** → Day 4 depends on Day 9's DTO. That DTO already ships in the starter; if you deleted it, run `cp -R day1-solved-files/backend/ backend/` to restore.
- **Boot fails on Hibernate schema validation for `audit_log`** → Day-1 fix missing. Overlay Day 1.
- **`ConstraintViolationException` handler doesn't fire** → validation isn't being triggered on that endpoint. Add `@Valid` to the controller parameter.
- **Port 8080 in use** → `lsof -i :8080` then `kill <PID>`.

Good progress — you're over the persistence hump. Onward to security.
