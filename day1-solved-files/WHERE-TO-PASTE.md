# Day 1 — Solved Files & How To Run

Hey! Welcome to Day 1. This folder holds the finished versions of every
file you touched today (Liquibase migrations for partitioning + the
audit-log fix), plus a couple of small backend tweaks. Copy them into
your project, boot the app, and watch the migrations do their thing.

**How this folder works**

The real `backend/` tree ships with Day-1 files as starter versions —
the Liquibase changelogs have placeholder single-partition changesets,
`application.yml` is missing the `server.port` override, and the
`SecurityConfig` still has `TODO(TICKET-…)` markers where JWT will
plug in on Day 5. This folder contains **complete drop-in replacement
files** for the Day-1 scope. You can:

- **Overlay** the whole `backend/` subtree in one shot (fastest), or
- **Open each file** in this folder side-by-side with the starter to
  read the diff first, then copy the solved version over.

Both flows land at the same result.

**In this file you'll find:**

1. A one-line command to drop the solved files into the right place.
2. A friendly walkthrough of what each file changed and why.
3. Step-by-step instructions to run the project on H2 (quick, no Docker) **or** Postgres (the full Day-1 experience).
4. What success looks like — the exact log lines you should see.
5. A short troubleshooting cheat-sheet for the usual bumps.

Take it slow, read the log messages, and if anything looks off, jump to
the troubleshooting section at the bottom before pinging your trainer.

---

## Quick start (TL;DR)

All paths below are relative to the **project root** (the directory that
contains `backend/`, `frontend/`, `db/`, `docker-compose.yml`).

The folder structure inside `day1-solved-files/` mirrors the project layout,
so you can copy the whole `backend/` tree over the top of the real
`backend/` tree and every file lands in the right place automatically.

```bash
# From the project root — one-shot overlay:
cp -R day1-solved-files/backend/ backend/
chmod +x backend/mvnw
```

Then jump to **"Run the project"** below.

---

## File-by-file map

| # | File in `day1-solved-files/` | Paste into (project-root path) | Ticket | Day |
|---|------------------------------|--------------------------------|--------|-----|
| 1 | `backend/src/main/resources/db/changelog/changes/004-partitioning.xml` | `backend/src/main/resources/db/changelog/changes/004-partitioning.xml` | TICKET-ADV007 | Day 1 |
| 2 | `backend/src/main/resources/db/changelog/changes/006-audit-and-recon.xml` | `backend/src/main/resources/db/changelog/changes/006-audit-and-recon.xml` | TICKET-ADV007 (FK split) | Day 1 |
| 3 | `backend/src/main/resources/application.yml` | `backend/src/main/resources/application.yml` | Port override (8081) | — |
| 4 | `backend/src/main/java/com/dbtraining/reconx/security/SecurityConfig.java` | `backend/src/main/java/com/dbtraining/reconx/security/SecurityConfig.java` | TICKET-ADV073 (partial) | Day 5 |
| 5 | `backend/mvnw` | `backend/mvnw` | executable-bit fix (`chmod +x`) | — |

---

## What each change does

### 1. `004-partitioning.xml` — TICKET-ADV007

Converts `trades` into a `PARTITION BY RANGE (trade_date)` parent and
generates 12 rolling monthly partitions (`trades_YYYY_MM`).

- **Step 1** drops the non-partitioned `trades` from `002` (Postgres only,
  `CASCADE` removes the settlements FK).
- **Step 2** recreates `trades` as a range-partitioned parent. PK becomes
  `(id, trade_date)` and `UNIQUE (trade_ref, trade_date)` — Postgres
  requires the partition key in every unique constraint on the parent.
- **Step 3** uses a `DO $$ … $$` block to create `current_month - 11 .. current_month`
  partitions dynamically so hard-coded dates don't go stale.
- All three changesets are gated `<dbms type="postgresql"/>` and MARK_RAN on H2.

**Verify:**

```bash
docker compose exec postgres psql -U reconx -d reconx -c '\d+ trades'
# Should print "Partitioned table" plus 12 child partitions.
```

### 2. `006-audit-and-recon.xml` — TICKET-ADV007 side-effect + audit_log fix

Two changes:

- **`audit_log.before_state` / `after_state`** switched from `CLOB` to
  `VARCHAR(1000000)`. On H2 (Postgres mode) `CLOB` aliases to
  `CHARACTER LARGE OBJECT`, which fails Hibernate schema validation
  against the entity's `columnDefinition = "TEXT"` (Hibernate maps
  `String → Types.VARCHAR`). Unbounded VARCHAR keeps H2 happy and
  Postgres still stores it as `text`.
- **`recon_breaks` FK to `trades(id)` split into its own changeset**
  (`006-add-recon-breaks-fk-nonpartitioned`) so it can be skipped on
  Postgres — FKs into a partitioned parent must reference a uniquely
  indexed column set, and `id` alone is no longer unique on the parent
  after ADV007. The `<validCheckSum>ANY</validCheckSum>` lets DBs that
  already ran the pre-split version keep their `databasechangelog` row.

### 3. `application.yml` — port change

Adds `server.port: 8081` (default was 8080). Nothing more.

### 4. `SecurityConfig.java` — TICKET-ADV073 (**partial**, Day 5)

Adds the `PasswordEncoder` bean using `BCryptPasswordEncoder`. The other
two TODOs from ADV073 / ADV074 (JWT filter registration and
`@EnableMethodSecurity` + RBAC matchers) are **still open**.

### 5. `mvnw` — executable bit

Only the file mode changed (`100644 → 100755`). If Git on the target
machine drops the bit, run:

```bash
chmod +x backend/mvnw
```

---

## Run the project

You have two paths. **Path A (H2)** is the fastest way to see the app
alive and confirm the migrations don't blow up — no Docker, no
containers, everything in memory. **Path B (Postgres)** is the real
Day-1 experience — you'll see the actual 12 monthly partitions of the
`trades` table.

If you're short on time, do Path A. If you want to see the partitioning
magic from ADV007 for real, do Path B.

### Before you start — one-time checks

1. **Java 21.** Run `java -version`. If it says 21.x you're good. On macOS with multiple JDKs installed:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ```
2. **You're in the project root** — the folder that has `backend/`, `docker-compose.yml`, and `day1-solved-files/` side-by-side.
3. **You copied the solved files** — if not, do it now:
   ```bash
   cp -R day1-solved-files/backend/ backend/
   chmod +x backend/mvnw
   ```

### Path A — Run on H2 (fastest, no Docker)

```bash
cd backend
./mvnw clean spring-boot:run
```

That's it. The `dev` Spring profile is on by default and uses an
in-memory H2 database with PostgreSQL-compat mode. First boot takes
~4 seconds.

You should see, in the log, roughly:

```
liquibase.ui  : Liquibase: Update has been successful. Rows affected: 22
c.dbtraining.reconx.ReconxApplication : Started ReconxApplication in 3.8 seconds
o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8081
```

Now, in a second terminal, prove it's alive:

```bash
curl http://localhost:8081/api/actuator/health
# → {"status":"UP","groups":["liveness","readiness"]}
```

You can also open the H2 web console at
<http://localhost:8081/h2> (JDBC URL: `jdbc:h2:mem:reconx`, user `sa`,
no password) and run:

```sql
SELECT id, filename, description FROM databasechangelog ORDER BY orderexecuted;
```

You should see rows for both `004-partitioning.xml` (marked as ran) and
`006-audit-and-recon.xml` (all four sub-changesets).

To stop the app, hit `Ctrl+C` in the terminal where it's running.

**Heads-up:** on H2 the three Postgres-only partitioning changesets
`MARK_RAN` (they log `Marking ChangeSet ... as ran despite precondition
failure due to onFail='MARK_RAN'`). That is **intentional** — H2 can't
do declarative partitioning. To actually see 12 monthly partitions, do
Path B.

### Path B — Run on Postgres (the full Day-1 experience)

```bash
# 1. Boot Postgres via Docker Compose (from project root)
docker compose up -d postgres

# 2. Wait ~3 seconds for it to accept connections
docker compose logs postgres | tail -5   # look for "ready to accept connections"

# 3. Boot the app against Postgres (uat or prod profile)
cd backend
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
```

Once you see `Started ReconxApplication`, verify the partitioning
worked — this is the moment you should feel a little proud:

```bash
docker compose exec postgres psql -U reconx -d reconx -c '\d+ trades'
```

You should see:

```
Partitioned table "public.trades"
Partition key: RANGE (trade_date)
...
Partitions: trades_2025_08 FOR VALUES FROM ('2025-08-01') TO ('2025-09-01'),
            trades_2025_09 FOR VALUES FROM ('2025-09-01') TO ('2025-10-01'),
            ... (12 rows total, one per month for the last year)
```

That's TICKET-ADV007 landing for real. If you now `SELECT * FROM
mv_daily_recon_summary;` (after loading Day-1 seed data), you'll get
one row per trade date.

---

## What success looks like

Whether you took Path A or Path B, you're good when **all three** of
these are true:

- The startup log ends with `Started ReconxApplication in X seconds`.
- `curl http://localhost:8081/api/actuator/health` returns `{"status":"UP"}`.
- `databasechangelog` has rows for changesets `004-*` and `006-*` (either "ran successfully" on Postgres, or "Marking as ran" on H2 for the partitioning ones).

If those three are green, Day 1 is done. Take a break, get a drink,
then move on to Day 2.

---

## If something goes wrong

**"Cannot find or load main class"** — you're not on Java 21. Run
`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` and retry.

**"Port 8081 already in use"** — an old copy of the app is still
running. Find and kill it:
```bash
lsof -i :8081        # note the PID
kill <PID>
```

**"Liquibase: validation failed"** — usually means the checksum of a
changeset changed. If you edited an already-applied changeset by hand,
either add `<validCheckSum>ANY</validCheckSum>` (like `006-create-recon-breaks` does) or clear the changelog and start over. On H2 (in-memory) just restart the app — the DB is fresh every boot.

**"Hibernate schema validation failed for audit_log"** — you probably
didn't copy the new `006-audit-and-recon.xml`. Re-run the
`cp -R day1-solved-files/backend/ backend/` step and reboot.

**Postgres path: `permission denied` or `password authentication failed`** — check `docker compose logs postgres` and the credentials in `application-uat.yml`. Default in this project is user `reconx` / password `reconx`.

**Docker not installed / not running** — no worries, do Path A (H2) instead. You'll still verify that your Liquibase XML is well-formed and that the audit-log fix works.

Still stuck? Check the full boot log for the first `ERROR` line — that's
almost always the real cause. Copy that line to your trainer with a
one-sentence description of what you tried. That gets you unstuck
fastest.

Good luck — you've got this.
