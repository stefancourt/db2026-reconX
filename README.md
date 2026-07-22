# ReconX — Enterprise Trade Reconciliation Platform (Student Starter)

> Deutsche Bank — TDI 2026 Graduate Technical Training Programme
> **Advanced Track (Intermediate-Hybrid)** | 10-Day Case Study | Version 1.0

This repository is the **starter scaffold** for the ReconX case study. Each day
of the programme adds another layer to the system. By Day 10 you and your team
will have built, dockerised, tested, and monitored a near-production-grade
trade reconciliation platform with Kafka event streaming, JWT-backed RBAC, a
React 19 dashboard, and a CI/CD pipeline that ships Docker images to GHCR.

---

## What you will build

A near-production-grade trade reconciliation platform used (in concept) by an
Ops team to detect and resolve mismatches between internal trade records and
external counterparty/custodian feeds — built across 10 days, 165 tickets.

```
       ┌──────────┐        ┌──────────────────────────┐        ┌────────────┐
       │  React   │  HTTPS │  Spring Boot REST API    │  JDBC  │ PostgreSQL │
       │ Frontend │ ─────▶ │  recon-service (Java 25) │ ─────▶ │  (Liqui-   │
       │  + Vite  │        │  + Spring Security/JWT   │        │   base     │
       └────┬─────┘        │  + Actuator/Micrometer   │        │   migs)    │
            │              └────────┬─────────────────┘        └─────┬──────┘
            │ SSE                   │  KafkaTemplate / @KafkaListener│
            │                       ▼                                ▼
            │              ┌──────────────────┐               ┌────────────┐
            └──────────────│  Apache Kafka    │               │ recon_*    │
                           │  trade-events    │               │ audit_log  │
                           │  recon-results   │               │ mat. views │
                           │  system-alerts   │               └────────────┘
                           │  + DLQ topics    │
                           └────────┬─────────┘
                                    ▼
                           ┌─────────────────────────┐
                           │ ReconConsumer (auto-rec)│
                           │ AuditConsumer (history) │
                           │ AlertConsumer  (notify) │
                           └─────────────────────────┘

  /actuator/prometheus ─▶ Prometheus (scrape) ─▶ Grafana dashboards + alerts
```

---

## Repository layout

```
reconx-studentCopy/
├── db/                            ← Day 1: standalone SQL assets
│   ├── queries.sql                ← Analytical queries (window fns, CTEs, JSONB)
│   ├── partitioning.sql           ← Monthly trade partitions
│   └── erd.md                     ← Mermaid ER diagram
│
│   NOTE: Liquibase changelogs live on the JVM classpath at
│         backend/src/main/resources/db/changelog/ — not here.
│
├── backend/                       ← Days 2-6, 9: Java 25 + Spring Boot 3 + Kafka
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/dbtraining/reconx/
│       ├── ReconxApplication.java
│       ├── model/                 ← Day 2-3: sealed TradeType hierarchy, value objects
│       ├── repository/            ← Day 4-5: Spring Data JPA + Specifications
│       ├── service/               ← Day 3-6: reconciliation engine, analytics
│       ├── controller/            ← Day 5: REST API endpoints
│       ├── dto/                   ← Request/response DTOs, TradeEvent, MapStruct mappers
│       ├── exception/             ← Custom hierarchy + @RestControllerAdvice
│       ├── config/                ← Swagger, JPA, Liquibase, Cache, Kafka config
│       ├── security/              ← Day 5: JWT filter, RBAC
│       ├── kafka/                 ← Day 9: producers, consumers, DLQ
│       └── observability/         ← Day 6: custom Micrometer metrics
│
├── static-dashboard/              ← Day 7: vanilla HTML/CSS/JS (pre-React exercise)
│   ├── dashboard.html
│   ├── trades.html
│   ├── recon.html
│   ├── css/style.css
│   └── js/*.js
│
├── frontend/                      ← Day 8-9: React 19 + Vite recon-ui
│   ├── package.json
│   ├── vite.config.js
│   ├── Dockerfile
│   └── src/
│       ├── App.jsx
│       ├── components/            ← DataTable (compound), TradeRow, StatCard, …
│       ├── hooks/                 ← useWebSocket, useTradeStream, useDebouncedSearch
│       ├── context/               ← ThemeProvider, AuthProvider
│       ├── services/              ← apiService.js
│       └── pages/                 ← Dashboard, Trades, Login, AddTrade
│
├── monitoring/                    ← Day 6 + 10: Prometheus / Grafana
│   ├── prometheus/prometheus.yml
│   └── grafana/provisioning/
│
├── .github/workflows/ci.yml       ← Day 10: GitHub Actions pipeline
├── docker-compose.yml             ← Day 10: 7-service stack
├── .env.example                   ← Sample environment variables
└── student-guides/                ← What you read each day
```

The full per-day walkthrough lives in
[`./student-guides/`](./student-guides/README.md).
**Read [`student-guides/day0/README.md`](./student-guides/day0/README.md)
before you start.**

---

## Prerequisites

- **Java 25** (Temurin recommended — the Advanced Track uses sealed classes, records, virtual threads where they fit)
- **Maven 3.9+**
- **Node.js 20+** and npm
- **Docker Desktop** (allocate ≥ 6 GB RAM — Kafka + Postgres + Prometheus + Grafana is heavier than Intermediate)
- **PostgreSQL 16** client tools (or use the bundled Docker container)
- **Git**
- IDE: IntelliJ IDEA Ultimate (backend) + VS Code (frontend) recommended

---

## Quick start (after Day 4)

```bash
# 1. Bring up infrastructure (Postgres + Kafka + Prometheus + Grafana + Kafdrop)
docker compose up -d postgres kafka zookeeper prometheus grafana kafdrop

# 2. Run the backend (Liquibase runs migrations automatically on startup)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Run the frontend
cd ../frontend
npm install
npm run dev

# 4. Open
# - Swagger UI:      http://localhost:8080/swagger-ui.html
# - Frontend:        http://localhost:5173
# - Prometheus:      http://localhost:9090
# - Grafana:         http://localhost:3000   (admin / admin)
# - Kafdrop:         http://localhost:9000
# - Actuator health: http://localhost:8080/actuator/health
```

### Default credentials (dev profile only, after you implement Day 5)

| Role          | Username        | Password     |
|---------------|-----------------|--------------|
| ADMIN         | `admin@db.com`  | `admin123`   |
| TRADER        | `trader@db.com` | `trader123`  |
| VIEWER        | `viewer@db.com` | `viewer123`  |
| RECON_ANALYST | `recon@db.com`  | `recon123`   |

JWT issued from `POST /api/auth/login` is valid for 60 minutes. Refresh tokens
live in HttpOnly cookies for 7 days.

---

## Deploy to the demo laptop (Day 10)

The deploy story is **GitHub Actions builds + pushes Docker images to GHCR;
the demo laptop pulls them and runs the full stack via `docker compose up`.**
No cloud hosting, no PaaS — the demo laptop *is* the deploy target.

```bash
# One-time on the demo laptop (uses a GitHub PAT with read:packages scope):
echo "<your-PAT>" | docker login ghcr.io -u <gh-username> --password-stdin

# Each deploy:
docker compose pull        # fetches the latest CI-tested images from GHCR
docker compose up -d       # brings up all 7 services
```

Full walkthrough: [`student-guides/day10/README.md`](./student-guides/day10/README.md).

---

## How to read the TODOs in this codebase

Every place you must write code has a comment block that looks like this:

```java
// ============================================================================
// TICKET-ADV019 — Build EquityTrade with the Builder pattern
//
// WHAT:    A concrete EquityTrade record/class that extends Trade and is
//          constructed via an immutable builder.
// HOW:     Use a static inner Builder with fluent setters returning `this`;
//          build() validates and returns an EquityTrade. Mark final fields.
// WHY:     Builder pattern keeps the call-site readable for trades with 8+
//          fields and gives us a single place to enforce invariants.
// OBSERVE: A trade missing required fields throws IllegalStateException at
//          build(), NOT at field-set time. Verify with the unit test in
//          EquityTradeTest.builder_missingPrice_throws.
// HINT:    See ../model/FXTrade.java for the same pattern applied to a
//          two-currency trade.
// ============================================================================
```

Below each block the method body is replaced with `// TODO(TICKET-IHxxx)` and
either an `UnsupportedOperationException` or a minimal placeholder return.
Your job is to remove the TODO and implement the body.

The full ticket text, acceptance criteria, and step-by-step hints live in the
matching day's README under [`./student-guides/`](./student-guides/README.md).

---

## Daily flow

| Day | Theme | New Tickets | Headline new-2026 topic |
|----:|-------|-------------|--------------------------|
| 0   | Introduction & onboarding | — | — |
| 1   | PostgreSQL + Liquibase Deep Dive | ADV001–ADV017 | ★ Liquibase, ★ AI for ADR |
| 2   | Java OOP + sealed classes + SOLID | ADV018–ADV032 | sealed-class trade hierarchy |
| 3   | Functional Java + JUnit 5 + Testcontainers | ADV033–ADV047 | parallel recon with CompletableFuture |
| 4   | Spring Boot enterprise setup | ADV048–ADV062 | multi-module Maven, Hibernate Envers, MapStruct |
| 5   | REST + JWT + RBAC + Testcontainers tests | ADV063–ADV080 | API versioning |
| 6   | Caching + Prometheus + Grafana | ADV081–ADV097 | ★ Observability deep dive |
| 7   | HTML5 + CSS Grid + SSE feed + ARIA | ADV098–ADV110 | ★ live SSE trade feed |
| 8   | JS ES6+ + React patterns (HOC, hooks, RHF) | ADV111–ADV125 (+ ADV127 stretch) | React performance profiling |
| 9   | React Context + Kafka multi-topic + DLQ | ADV128–ADV145 | ★ Kafka deep dive, event sourcing |
| 10  | Docker (7-svc) + GH Actions + load test + demo | ADV146–ADV165 | ★ Liquibase-in-CI, ★ AI in DevOps |

---

## Branching

Use **GitFlow**:

```
main      ← only release tags (v1.0.0 at end of Day 10)
develop   ← integration branch — your team merges here
feature/* ← one branch per ticket (e.g. feature/ADV019-equity-builder)
```

Open a Pull Request from each `feature/*` branch into `develop`. Two approvals
required before merge (advanced track convention — Intermediate only required
one).

---

## Final demo (Day 10)

A 20-minute end-to-end walkthrough:

| Minutes | Content |
|--------:|---------|
| 3       | Problem statement + C4 architecture diagram |
| 8       | Live demo: JWT login → post trade → Kafka event → auto-recon → resolve break → Grafana metric ticks |
| 5       | Code walkthrough (one feature each team member is proud of) |
| 4       | Q&A |

---

## Good luck — and ask your instructors anything 🏦
