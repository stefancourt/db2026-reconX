# Day 5 — Solved Files & How To Run

Day 5 is the security day — you turn the permissive Day-1 filter chain
into a real JWT + RBAC setup, and you flesh out the four REST
controllers behind it.

**How this folder works**

The real `backend/` tree ships the three security files as starter
stubs — method bodies do `throw new UnsupportedOperationException("…")`
with a `TODO(TICKET-…)` comment above each. This folder contains
**complete drop-in replacement files** for those three:

- `JwtTokenProvider` — HS256 token generate/parse using jjwt 0.12.
- `JwtAuthenticationFilter` — reads `Authorization: Bearer …`, parses the token, populates `SecurityContextHolder`.
- `SecurityConfig` — stateless JWT filter chain with role-based matchers for `/v1/trades/**`, `/v1/recon/**`, `/v1/audit/**`, plus `@EnableMethodSecurity`.

You can **overlay** the whole `backend/` subtree in one shot, or
**open each file** in this folder side-by-side with the starter to
read the diff first.

**What is deliberately left as your exercise (TODOs still open in your own code):**

- The four REST controllers (`AuthController`, `TradeController`, `ReconController`, `AuditController`) — 11 small method bodies, each is a straightforward service delegation. The hint pseudocode is inline in each TODO block in your own code, so you can fill them in from the stubs alone without opening the student guide. See the "Finish the controllers" section below.

**In this file:**

1. One-line copy command.
2. Ticket status table.
3. What each shipped file does.
4. Step-by-step run guide — including a curl walkthrough that proves JWT + RBAC are enforcing.
5. Guidance for finishing the four controllers.
6. Troubleshooting.

---

## Quick start

```bash
# From the project root:
cp -R day5-solved-files/backend/ backend/
```

---

## Ticket status

| Ticket | Status | Where |
|---|---|---|
| ADV063 — REST controller skeletons | ✓ already in starter | `controller/*.java` |
| ADV064–067 — TradeService CRUD | ✓ Day-4 folder | `service/TradeService.java` |
| ADV068 — POST /v1/recon/run | ⏳ TODO in your code | `ReconController.runRecon` |
| ADV069 — GET /v1/recon/jobs/{id}/results | ⏳ already returns `[]` | `ReconController.results` |
| ADV070 — PUT /v1/recon/results/{id}/resolve | ⏳ TODO in your code | `ReconController.resolve` |
| ADV071 — GET /v1/audit/trades/{ref} | ⏳ already returns `[]` | `AuditController.history` |
| ADV072 — JwtTokenProvider | ✓ in this folder | `security/JwtTokenProvider.java` |
| ADV072 — POST /auth/login | ⏳ TODO in your code | `AuthController.login` |
| ADV073 — JwtAuthenticationFilter | ✓ in this folder | `security/JwtAuthenticationFilter.java` |
| ADV074 — SecurityConfig RBAC + `@EnableMethodSecurity` | ✓ in this folder | `security/SecurityConfig.java` |
| ADV075/076 — CORS + rate limiting | ✓ dependency-managed in starter | pom.xml + config |
| ADV077–080 — API versioning, DTO validation, PATCH shape | ✓ already in starter | `controller/*.java` |

---

## What each shipped file does

### `JwtTokenProvider.java`

Wraps jjwt 0.12. `generate(email, role)` signs an HS256 JWT whose
subject is the user's email, whose issuer is the configured
`reconx.security.jwt.issuer`, and whose expiry comes from
`reconx.security.jwt.expiration-minutes`. Role goes into a custom
`"role"` claim. `parse(token)` verifies signature + issuer and returns
the `Claims`. The secret must be ≥ 256 bits (HS256 requirement) — the
default in `application.yml` is fine for dev.

### `JwtAuthenticationFilter.java`

Extends `OncePerRequestFilter` so it runs exactly once per request.
Reads `Authorization: Bearer …`, parses via `JwtTokenProvider`,
wraps the role as `ROLE_<name>` and stores a
`UsernamePasswordAuthenticationToken` in `SecurityContextHolder`. On a
bad or expired token it clears the context (rather than throwing) — so
the request continues, and Spring's normal auth path produces a clean
401/403 when the request hits a protected endpoint.

### `SecurityConfig.java`

Stateless filter chain (`SessionCreationPolicy.STATELESS`), CSRF
disabled (safe for a stateless JWT API), H2 console frameOptions
disabled (dev-only convenience), and the RBAC matchers below:

| Path | Roles |
|---|---|
| `/auth/login`, `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`, `/swagger-ui/**`, `/v3/api-docs/**`, `/h2/**` | permitAll |
| `GET /v1/trades/**` | VIEWER, TRADER, RECON_ANALYST, ADMIN |
| `POST /v1/trades` | TRADER, ADMIN |
| `PUT /v1/trades/**`, `PATCH /v1/trades/**` | TRADER, ADMIN |
| `DELETE /v1/trades/**` | ADMIN only |
| `/v1/recon/**` | RECON_ANALYST, ADMIN |
| `/v1/audit/**` | RECON_ANALYST, ADMIN |
| everything else | authenticated |

`@EnableMethodSecurity` on the class enables `@PreAuthorize` on service
methods.

`JwtAuthenticationFilter` is registered before
`UsernamePasswordAuthenticationFilter`.

---

## Run the project

### Before you start

1. **Java 21.** `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
2. **You're in the project root.**
3. **You copied the solved files:** `cp -R day5-solved-files/backend/ backend/`.
4. **Days 1–4 are applied** — Day 5 depends on the audit-log fix, sealed hierarchy, recon engine, and the persistence layer:
   ```bash
   cp -R day1-solved-files/backend/ backend/
   cp -R day2-solved-files/backend/ backend/
   cp -R day3-solved-files/backend/ backend/
   cp -R day4-solved-files/backend/ backend/
   ```

### Step 1 — Compile

```bash
cd backend
./mvnw -q clean compile   # want exit 0
```

### Step 2 — Boot

```bash
./mvnw spring-boot:run
```

Wait for `Started ReconxApplication in ~4 seconds`.

### Step 3 — Prove the security is enforcing

```bash
# permitAll — should return 200 with {"status":"UP"}
curl -i http://localhost:8081/api/actuator/health

# protected — anonymous request should be rejected (403 without JWT)
curl -i http://localhost:8081/api/v1/trades
```

If you get `200` on the health endpoint and `403` (or `401`) on
`/v1/trades`, the JWT + RBAC chain is doing its job.

Once you fill in `AuthController.login`, you'll be able to:

```bash
# Exchange credentials for a JWT
curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@reconx.local","password":"password"}' | jq -r .token

# Then use it
TOKEN=$(...as above...)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/trades
```

Hit `Ctrl+C` when done.

---

## Finish the controllers (your exercise)

Four files, 11 TODOs, all straightforward. Each TODO in your own code
already has hint pseudocode inline — you don't need to open the student
guide to solve them. Here's the map:

| File | Method | What to do |
|---|---|---|
| `AuthController.login` | POST /auth/login | `users.findByEmail(req.email())`, `encoder.matches(req.password(), user.passwordHash())`, then `jwt.generate(email, role)` and return `new LoginResponse(token, "Bearer", jwt.expirationSeconds(), role)`. On mismatch, `throw new InvalidTradeException("Invalid credentials")` — do NOT leak which check failed. |
| `TradeController.list/get/create/update/updateStatus/softDelete/exportCsv/importCsv` | GET/POST/PUT/PATCH/DELETE `/v1/trades` | Each delegates to `tradeService.<method>(...)` and maps the result via `TradeMapper` where needed. The CSV import/export methods already have full implementations in the starter. |
| `ReconController.runRecon` | POST /v1/recon/run | Generate a `String jobId = UUID.randomUUID().toString()`, persist a `recon_jobs` row (repo already exists), return `ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId, "status", "QUEUED"))`. |
| `ReconController.resolve` | PUT /v1/recon/results/{id}/resolve | Load `ReconBreak` by id (`.orElseThrow(() -> new TradeNotFoundException(id.toString()))`), call `rb.resolve(body.get("note"))`, save, return the entity. |
| `AuditController.history/events` | GET /v1/audit/trades/{ref}[/events] | `return auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);` for both. |

After you paste those in, restart the app and hit `/api/swagger-ui.html`
to try the endpoints interactively — the green "Authorize" button
accepts the JWT you got from `/auth/login`.

---

## Troubleshooting

- **`WeakKeyException` at startup** — your `JWT_SECRET` env var is shorter than 32 bytes. Use the default in `application.yml` for dev.
- **All requests return 403 including `/auth/login`** — you overlaid `SecurityConfig` but didn't include `JwtAuthenticationFilter` on the classpath. Confirm all three security files came over.
- **`Cannot resolve @EnableMethodSecurity`** — Spring Security 6.x moved this annotation; ensure Spring Boot parent is 3.x (already true in this project).
- **Port 8081 in use** — `lsof -i :8081; kill <PID>`.
- **Overlay lost the Day-1 audit-log fix** — re-run `cp -R day1-solved-files/backend/ backend/`.

You're through the security wiring — the hard part. The controllers
are a warm-down.
