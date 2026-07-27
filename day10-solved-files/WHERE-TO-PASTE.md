# Day 10 — Solved Files & How To Run

Day 10 is release day. You put the whole stack in containers, wire it
to Prometheus + Grafana, gate CI on coverage + static analysis + a k6
load test, take screenshots for the demo deck, and tag `v1.0.0`.

**How this folder works**

Day 10 is about infrastructure files (Dockerfiles, compose, monitoring
YAML/JSON) — no `TODO(TICKET-…)` markers to fill in. This folder
contains **complete drop-in versions** of every infra file the day
needs. Overlay them into the project root with the targeted `cp`
commands in Quick Start below; each file lands next to (or replaces)
its counterpart in the real tree.

**What this folder ships** (a snapshot of the current infra + release
files at their real project-root paths):

- `docker-compose.yml` — 7-service stack (ADV148).
- `backend/Dockerfile` — multi-stage build (ADV146).
- `frontend/Dockerfile` + `frontend/nginx.conf` — SPA container + SPA-fallback rewrite (ADV147).
- `monitoring/prometheus/prometheus.yml` — scrape config (ADV149).
- `monitoring/prometheus/alerts.yml` — Prometheus alert rules.
- `monitoring/grafana/provisioning/datasources/prometheus.yml` — Grafana datasource (ADV150).
- `monitoring/grafana/provisioning/dashboards/reconx.yml` + `reconx-overview.json` — dashboard provisioning + baseline dashboard.

Everything **not** in this folder is genuinely process — CI workflow,
JaCoCo/Checkstyle plugin blocks, k6 script, screenshots, README, demo
deck, retro. Those are yours to write; the sections below map out
where each one lives and how to verify.

## Quick start

```bash
# From the project root — targeted overlays:
cp day10-solved-files/docker-compose.yml .
cp -R day10-solved-files/monitoring .
cp day10-solved-files/backend/Dockerfile backend/
cp day10-solved-files/frontend/Dockerfile frontend/
cp day10-solved-files/frontend/nginx.conf frontend/
```

---

## Ticket status

Day 10 has 20 tickets (ADV146–165). They fall in four buckets:

**Containers + compose**

| Ticket | What to do | Where |
|---|---|---|
| ADV146 | Backend multi-stage Dockerfile (BUILD → RUNTIME with `eclipse-temurin:21-jre-alpine`) | `backend/Dockerfile` |
| ADV147 | Frontend multi-stage Dockerfile + `nginx.conf` for SPA fallback | `frontend/Dockerfile`, `frontend/nginx.conf` |
| ADV148 | `docker-compose.yml` with 7 services (postgres, kafka, kafdrop, backend, frontend, prometheus, grafana) + healthchecks + volumes | `docker-compose.yml` (starter has the skeleton) |
| ADV151 | Liquibase migrations run on container start (they already do — it's a boot-time check) | `application.yml` `spring.liquibase.enabled` |
| ADV152 | Every container's healthcheck goes green in isolation | inspect via `docker compose ps` |
| ADV153 | Compose smoke-test script — bring up, wait for health, curl a couple of endpoints, tear down | `scripts/smoke-test.sh` |

**Observability**

| Ticket | What to do | Where |
|---|---|---|
| ADV149 | Prometheus scrape config pointing at `backend:8081/api/actuator/prometheus` | `monitoring/prometheus.yml` (starter has skeleton) |
| ADV150 | Grafana provisioning — datasource + dashboards JSON | `monitoring/grafana/` |
| ADV159 | Take screenshots — baseline, under-load, recovery — for the deck | any `screenshots/` folder |

**CI + quality gates**

| Ticket | What to do | Where |
|---|---|---|
| ADV154 | GitHub Actions workflow — build/test/push image to GHCR | `.github/workflows/ci.yml` |
| ADV155 | Liquibase `validate` step in CI before deploy | same workflow |
| ADV156 | JaCoCo Maven plugin with an 85 % line-coverage gate | `backend/pom.xml` `<plugin>org.jacoco:jacoco-maven-plugin</plugin>` |
| ADV157 | Checkstyle static analysis, fail build on violations | `backend/pom.xml` `<plugin>maven-checkstyle-plugin</plugin>` + `checkstyle.xml` |
| ADV158 | k6 load test — 200 concurrent trade creations for 60s | `load-tests/trade-creation.js` |

**Docs + release**

| Ticket | What to do | Where |
|---|---|---|
| ADV160 | Mermaid architecture diagrams (Context, Container, Component levels — refer back to Day-1 C4 work) | `db/diagrams/` |
| ADV161 | Comprehensive top-level `README.md` — quickstart, architecture, how to run each layer | `README.md` |
| ADV162 | Demo deck (10 slides, `.pptx` or `.pdf`) | any location, link from README |
| ADV163 | 20-minute rehearsal runsheet — run it twice | `docs/demo-runsheet.md` |
| ADV164 | Tag `v1.0.0` after CI green on `main` | `git tag -a v1.0.0 -m "…"; git push origin v1.0.0` |
| ADV165 | Retrospective — what worked, what didn't, what to change | `docs/retro.md` |

---

## Run the project (full stack via compose)

Once Days 1–9 backend work is applied, the whole stack should come up
with one command:

```bash
cd /Users/siddharthsharma/Downloads/OneDrive_1_22-07-2026

# 1. build the backend jar so the Dockerfile picks up target/*.jar
cd backend && ./mvnw -q clean package -DskipTests && cd ..

# 2. bring up the whole stack
docker compose up -d --build

# 3. watch health come green (takes ~30-60s)
watch 'docker compose ps'
```

Then open:

- Backend Swagger UI — <http://localhost:8081/api/swagger-ui.html>
- Frontend SPA — <http://localhost:3000> (or whatever port `docker-compose.yml` maps for the frontend)
- Kafdrop — <http://localhost:9000>
- Prometheus — <http://localhost:9090>
- Grafana — <http://localhost:3001> (admin/admin, then Datasources → Prometheus, then Dashboards → ReconX)

Tear down: `docker compose down -v` (the `-v` also drops volumes).

### CI verification locally

```bash
cd backend
./mvnw verify -Pci        # runs tests + JaCoCo report + Checkstyle
open target/site/jacoco/index.html   # look for >= 85% Total line coverage
```

### k6 load test

```bash
# Requires k6 installed: brew install k6
k6 run load-tests/trade-creation.js
```

Watch Grafana while it runs — you should see `trade_created_total`
climbing, `reconciliation_duration_seconds` percentiles moving, and
CPU/heap on the JVM dashboards react.

### Tag the release

```bash
git tag -a v1.0.0 -m "Release 1.0.0 — ReconX Advanced Track"
git push origin v1.0.0
```

CI then builds the image with the version tag and pushes to GHCR
(assuming your GH secrets are wired).

---

## What success looks like

- `docker compose up -d --build` brings every service to Healthy in ≤ 90s.
- `curl -f http://localhost:8081/api/actuator/health` returns 200.
- Frontend loads at the mapped port with no console errors.
- Kafdrop lists the four topics; posting a trade puts a message on `trade-events`.
- Grafana's ReconX dashboard renders panels with real metric data.
- `./mvnw verify` in the backend hits the JaCoCo 85 % gate.
- k6 run finishes with `http_req_failed` < 1 % and p95 latency inside your SLA.
- CI on the `v1.0.0` tag pushes an image to `ghcr.io/<org>/reconx:v1.0.0`.

---

## Troubleshooting

- **`docker compose up` hangs on a service** — `docker compose logs <service>` and read the last 30 lines. Common: Postgres not ready before backend starts; add `depends_on: postgres: condition: service_healthy`.
- **Backend container exits with `IllegalArgumentException: JWT_SECRET`** — the container env is missing the secret. Set it in `docker-compose.yml` under `backend.environment`.
- **Prometheus panels are all `No data`** — Prometheus can't scrape the backend. Confirm the target in `monitoring/prometheus.yml` uses the service name (`backend`) not `localhost`.
- **Grafana dashboards are empty** — data source URL uses `localhost` instead of `prometheus`; the docker network resolves service names, so use `http://prometheus:9090`.
- **JaCoCo coverage below 85 %** — the untested branches are usually in exception handlers. Add a couple of RTL/JUnit tests around the negative paths.
- **k6 run reports lots of 401** — the token generated at start expires mid-run. Regenerate inside the setup() or extend expiration.

Ship it. Retro doc is where the real learning gets captured — don't
skip it.
