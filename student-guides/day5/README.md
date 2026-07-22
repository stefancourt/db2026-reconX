# Day 5 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day5/README.md](../../TrainersGuide/day5/README.md)
> **Module:** Spring Boot Modules 3 & 4 — REST + JWT Security

## What you'll build today

Today is the day ReconX gets a public face. You will ship the HTTP surface
for trades, reconciliation jobs, and audit history, then lock it down with
JSON Web Tokens and role-based access control. By the end of the day,
unauthenticated callers get a 401, role mismatches get a 403, and an
admin can drive a trade through its full lifecycle — create, list, patch,
trigger a recon run, resolve a break — using only `curl` and a Bearer
token. You will also prove the whole thing works end-to-end with MockMvc
slice tests and a Testcontainers integration test that spins up a real
Postgres, applies Liquibase migrations on a fresh database, and exercises
every endpoint over real HTTP.

## Day at a glance

1. Standup and Day-4 holdover unblock
2. AM mini-lecture: Spring MVC request lifecycle, `@Valid`, pagination
3. Workshop 5A: REST CRUD endpoints (TICKET-ADV063 – TICKET-ADV071)
4. Lunch
5. PM mini-lecture: Spring Security filter chain, stateless auth
6. Workshop 5B: JWT + RBAC (TICKET-ADV072 – TICKET-ADV074)
7. Workshop 5C: MockMvc + Testcontainers + versioning (TICKET-ADV075 – TICKET-ADV080)
8. Buffer / unblock
9. End-of-day debrief and Day-6 preview

**Hard rule for the day:** no controller ships without (a) `@Valid` on the
request body, (b) at least one MockMvc test, and (c) an entry in the
`SecurityFilterChain`. If any of those three is missing, the work is not
done yet.

## Exercises

Eighteen exercises, three workshop blocks. Each exercise gives you a
goal, a verifiable "done when" checklist, and three progressive hints —
a gentle direction, a concrete pointer, then a near-solution shape.
Try the goal first. Open the hints one at a time, only when you are
stuck. Resist the temptation to skim all three before writing any code:
the hints are calibrated to leave you with something to figure out.

### Workshop 5A — REST CRUD endpoints

This block builds `TradeController`, `ReconController`, and
`AuditController`. Nine endpoints, every one wired through `@Valid`,
pagination, and proper status codes.

---

### TICKET-ADV063 — GET /api/v1/trades (paginated, filterable, sortable)

**Goal:** Expose a single GET endpoint that returns trades with optional filters and stable pagination metadata.

**What**
- `TradeController.list(...)` exposing `GET /api/v1/trades` that returns a `PagedResponse` envelope built from `Page<Trade>` via `JpaSpecificationExecutor` filters on `status`, `counterparty`, plus Spring Data `Pageable`.

**Why**
- Every other Day 5 read endpoint reuses this paging envelope, and Day 8's React table binds directly to `content` / `totalPages` — get the shape wrong here and the frontend wiring later breaks.

**Observe**
- `curl 'http://localhost:8080/api/v1/trades?sort=createdAt,desc' | jq '.totalPages'` returns an integer; a 500 with `PropertyReferenceException: No property xyz` means the sort field does not match a camelCase JPA attribute.

**Done when:**
- Calling `GET /api/v1/trades` returns a JSON object with `content`, `page`, `size`, `totalElements`, `totalPages`, and `last` fields.
- Passing `?status=PENDING&counterparty=Apex` narrows the result set without breaking when only one filter is supplied.
- `?sort=createdAt,desc` orders newest first and does not throw a 500 about an unknown property.

<details>
<summary>Hint 1 — gentle direction</summary>

Look at the JSON Postman shows when you call the endpoint. If you can see
the rows but nothing about `totalPages`, you are returning a raw list and
losing the pagination metadata. What shape would let a client know there
are more pages?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`JpaRepository.findAll(Pageable)` returns a `Page<T>` — that wrapper
carries the metadata you need. The sort property must match the JPA
field name (camelCase), not the SQL column name. For combining optional
filters, look at `JpaSpecificationExecutor` and Spring Data's
`Specification` API instead of string-concatenating a `WHERE` clause.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Bind a `Pageable` directly on the controller method with
`@PageableDefault`. Accept each filter as an `Optional<…>` request
parameter. Translate the `Page<Entity>` returned by the service into a
small stable `PagedResponse<TradeResponse>` record that exposes content,
page, size, totalElements, totalPages, last — so the wire format does
not change if Spring's `PageImpl` Jackson shape changes between versions.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create the `PagedResponse<T>` record DTO with a static `from(Page<T>)` factory that flattens content/page/size/totalElements/totalPages.
2. Annotate `TradeController` with `@RestController` and `@RequestMapping("/v1/trades")`.
3. Add the `list(...)` method that accepts optional `from`/`to`/`status`/`counterpartyId` query params plus a `@PageableDefault` `Pageable`.
4. Delegate to `service.list(from, to, status, counterpartyId, pageable)` and wrap the returned `Page<Trade>` in `PagedResponse.from(page, mapper::toResponse)`.
5. Boot the app and hit `GET /api/v1/trades?status=PENDING&sort=tradeDate,desc` — confirm `totalElements`, `totalPages`, and `items` appear in the JSON.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/dto/PagedResponse.java`):

```java
package com.dbtraining.reconx.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Tiny wrapper that flattens Spring Data Page<T> into a
 * JSON-friendly shape. Avoids exposing Spring Data internals to clients.
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PagedResponse<T> from(Page<S> src, java.util.function.Function<S, T> mapper) {
        return new PagedResponse<>(
                src.getContent().stream().map(mapper).toList(),
                src.getNumber(),
                src.getSize(),
                src.getTotalElements(),
                src.getTotalPages()
        );
    }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/TradeController.java`):

```java
@GetMapping
@Operation(summary = "List trades — paginated, filterable, sortable")
public PagedResponse<TradeResponse> list(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long counterpartyId,
        @PageableDefault(size = 20, sort = "tradeDate", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<Trade> page = service.list(from, to, status, counterpartyId, pageable);
    return PagedResponse.from(page, mapper::toResponse);
}
```

</details>

**▶ Run the project — verify TICKET-ADV063 end-to-end**

Boot the app and confirm the paginated list endpoint returns the expected envelope shape.

```bash
docker compose up -d postgres
./mvnw -pl backend spring-boot:run &
sleep 25
curl -s "http://localhost:8080/api/v1/trades?page=0&size=20" | jq
```

**Observe:**

- JSON body contains `items` (or `content`), `page`, `size`, `totalElements`, `totalPages` fields.
- Default page size of 20 is respected when omitted; `page=0` returns the first page.
- A 500 about an unknown sort property means the sort parameter does not match a JPA field name.

---

### TICKET-ADV064 — POST /api/v1/trades (create + validation)

**Goal:** Create a new trade from a validated request body and return 201 with a `Location` header pointing to the new resource.

**What**
- `TradeController.create(...)` accepting `@Valid @RequestBody TradeRequest`, delegating to `TradeService.create(...)`, and returning `ResponseEntity.created(uri).body(response)` so the response carries HTTP 201 and a `Location: /api/v1/trades/{id}` header.

**Why**
- A clean create contract (201 + Location + ProblemDetail on validation failure) is what the MockMvc test in ADV075 and the Testcontainers lifecycle test in ADV078 assert against — and what the Day 8 React form expects to consume.

**Observe**
- `curl -i` on a valid POST shows `HTTP/1.1 201 Created` plus a `Location` header; an invalid body (missing `tradeRef`) returns `HTTP/1.1 400 Bad Request` with a `ProblemDetail` JSON body emitted by the Day 4 `GlobalExceptionHandler`.

**Done when:**
- Posting a valid `TradeRequest` returns HTTP 201, a `Location: /api/v1/trades/{id}` header, and the created trade as JSON.
- Posting an invalid body (missing `tradeRef`, negative `quantity`, future `tradeDate`) returns HTTP 400 with a `ProblemDetail` body.
- The controller method never sees a raw `Trade` entity — only the request DTO and the response DTO.

<details>
<summary>Hint 1 — gentle direction</summary>

What status code does REST convention require for a successful create?
Is it the same one you would use for a successful read? Where on the
HTTP response do you tell the caller where to find the new resource?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`ResponseEntity` has a static factory for "created" responses that
takes a `URI`. Look at `ResponseEntity.created(...)`. On the body side,
attach `@Valid` to the `@RequestBody` parameter so JSR-380 constraint
annotations on the request record fire — `@NotBlank`, `@NotNull`,
`@DecimalMin`, `@PastOrPresent`, `@Pattern`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

The controller method takes `@RequestBody @Valid TradeRequest req`,
delegates to the service, builds the location URI from the returned id,
and returns `ResponseEntity.created(uri).body(savedResponse)`. The Day-4
`@RestControllerAdvice` already converts the
`MethodArgumentNotValidException` into a `ProblemDetail` — you do not
need to handle validation errors in the controller.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Define the `TradeRequest` record DTO with JSR-380 annotations: `@NotNull`/`@Pattern` on `tradeRef`, `@Positive` on `quantity`, `@PositiveOrZero` on `price`, `@NotNull` on `tradeDate`.
2. Add `@PostMapping` on `TradeController.create(...)` taking `@Valid @RequestBody TradeRequest req` and `@AuthenticationPrincipal Object principal`.
3. Call `service.create(req, actor)` and capture the saved entity.
4. Build the URI `/api/v1/trades/{savedId}` and return `ResponseEntity.created(uri).body(mapper.toResponse(saved))`.
5. Test: `curl -X POST` with a valid payload returns 201 + `Location` header; an invalid payload returns 400 with a ProblemDetail body from the Day-4 advice.

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

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/TradeController.java`):

```java
@PostMapping
@Operation(summary = "Create a trade")
public ResponseEntity<TradeResponse> create(@Valid @RequestBody TradeRequest req,
                                            @AuthenticationPrincipal Object principal) {
    String actor = String.valueOf(principal);
    Trade saved = service.create(req, actor);
    return ResponseEntity
            .created(URI.create("/api/v1/trades/" + saved.getId()))
            .body(mapper.toResponse(saved));
}
```

</details>

**▶ Run the project — verify TICKET-ADV064 end-to-end**

POST a valid trade for 201, then a malformed body for 400.

```bash
# Valid body → 201 + Location header
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}'

# Invalid body (missing tradeRef, negative quantity) → 400 ProblemDetail
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{"quantity":-5,"price":245.50}'
```

**Observe:**

- Valid POST returns `HTTP/1.1 201 Created` with `Location: /api/v1/trades/{id}` header.
- Invalid POST returns `HTTP/1.1 400 Bad Request` with a `ProblemDetail` body listing field errors.
- A 500 instead of 400 means `@Valid` is not on the `@RequestBody` parameter.

---

### TICKET-ADV065 — PUT /api/v1/trades/{id} (full update)

**Goal:** Replace every mutable field of an existing trade in a single idempotent call.

**What**
- `TradeController.update(...)` mapped to `PUT /api/v1/trades/{id}` that reuses the validated `TradeRequest` from ADV064, copies every mutable field onto the loaded entity inside `@Transactional`, and throws `TradeNotFoundException` (mapped to 404) when the id is missing.

**Why**
- Idempotent full-replace semantics are what lets the Day 8 React edit form retry safely on a flaky network, and what the Day 4 `@RestControllerAdvice` 404 mapping was wired for.

**Observe**
- Two identical PUTs each return 200 with the same body; `PUT /api/v1/trades/9999999` returns `HTTP/1.1 404 Not Found` with a `ProblemDetail` body, not a 500 stack trace.

**Done when:**
- Calling `PUT /api/v1/trades/{id}` with a complete `TradeRequest` returns HTTP 200 and the updated representation.
- The same PUT called twice in a row leaves the resource in the same final state — no duplicate side effects.
- Targeting a non-existent id returns a 404 `ProblemDetail`, not a 500.

<details>
<summary>Hint 1 — gentle direction</summary>

PUT is the "replace the whole resource" verb. If the request body is
missing a field, what does that mean — null it out, or leave it alone?
Pick a clear answer and document it. Then think about what happens when
the id in the path does not exist: how does the caller learn that?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Reuse the same validated `TradeRequest` record from TICKET-ADV064 — same
constraints, same shape. The service should load the existing entity,
overwrite fields from the request, and rely on JPA dirty checking. For
the missing-id case, throw a custom `TradeNotFoundException` that your
Day-4 advice maps to 404.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

The controller signature is `update(@PathVariable Long id,
@RequestBody @Valid TradeRequest req)` returning
`ResponseEntity<TradeResponse>`. Inside `@Transactional`, find or throw,
copy every field from req onto the entity, return the mapped response.
Idempotency falls out for free because the second call writes the same
values.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Reuse `TradeRequest` from TICKET-ADV064 — same validation, same shape.
2. Add `@PutMapping("/{id}")` taking `@PathVariable Long id`, `@Valid @RequestBody TradeRequest req`, and `@AuthenticationPrincipal Object principal`.
3. In `TradeService.update(...)`, find the trade by id or throw `TradeNotFoundException` (mapped to 404 by `GlobalExceptionHandler`).
4. Inside `@Transactional`, overwrite every mutable field on the entity from the request and let JPA dirty-checking flush.
5. Return `mapper.toResponse(updated)` from the controller.
6. Test: PUT twice with the same body — the second call returns 200 with identical content (idempotent).

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/TradeController.java`):

```java
@PutMapping("/{id}")
@Operation(summary = "Full update of a trade")
public TradeResponse update(@PathVariable Long id, @Valid @RequestBody TradeRequest req,
                            @AuthenticationPrincipal Object principal) {
    return mapper.toResponse(service.update(id, req, String.valueOf(principal)));
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

</details>

**▶ Run the project — verify TICKET-ADV065 end-to-end**

PUT the same body twice and confirm idempotency, then PUT to a missing id for 404.

```bash
# Replace 1 with a real trade id from the previous list call
curl -i -X PUT http://localhost:8080/api/v1/trades/1 \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":150.0,"price":250.00,"tradeDate":"2026-03-15"}'

# Repeat the same PUT — should be idempotent
curl -i -X PUT http://localhost:8080/api/v1/trades/1 \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":150.0,"price":250.00,"tradeDate":"2026-03-15"}'

# Missing id → 404 ProblemDetail
curl -i -X PUT http://localhost:8080/api/v1/trades/9999999 \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0001","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":150.0,"price":250.00,"tradeDate":"2026-03-15"}'
```

**Observe:**

- Both successful PUTs return 200 with identical response bodies.
- Missing id returns `HTTP/1.1 404 Not Found` with a `ProblemDetail` body, not a 500.
- A 500 stack trace means `TradeNotFoundException` is not wired into the `@RestControllerAdvice`.

---

### TICKET-ADV066 — PATCH /api/v1/trades/{id}/status

**Goal:** Update only the `status` field of a trade through a dedicated sub-resource.

**What**
- `TradeController.patchStatus(...)` mapped to `PATCH /api/v1/trades/{id}/status` accepting a `{ "status": "MATCHED" }` body with a JSR-380 validated enum, calling `TradeService.updateStatus(...)`, and returning the updated `TradeResponse`.

**Why**
- A dedicated status sub-resource is the surface the ADV070 break-resolution flow and the Day 6 Kafka event-driven status transitions reuse — partial mutation belongs behind PATCH, not a wide PUT.

**Observe**
- Body `{"status":"FOOBAR"}` returns `HTTP/1.1 400 Bad Request` with a validation message naming the invalid enum value; ADV077 will assert that a VIEWER token gets 403 on this same path.

**Done when:**
- `PATCH /api/v1/trades/{id}/status` with a `{ "status": "MATCHED" }` body returns 200 and the updated trade.
- Sending an invalid status value (`FOOBAR`) returns 400 with a clear validation message.
- A VIEWER role cannot call this endpoint — RBAC blocks it before the controller runs (verified in TICKET-ADV077).

<details>
<summary>Hint 1 — gentle direction</summary>

Why is this not just another PUT on `/trades/{id}`? Think about
semantics: PUT means "here is the whole resource". A status flip is a
partial change. What verb communicates "modify a piece of the resource"?
And why is `/status` part of the URL path rather than a query parameter?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `@PatchMapping("/{id}/status")`. Build a tiny `StatusUpdate` record
with a single field constrained by `@NotBlank` and a `@Pattern` that
lists the legal status values. The service mutates only the `status`
field; everything else on the trade is untouched.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Controller takes `@PathVariable Long id` and `@RequestBody @Valid
StatusUpdate req`. Delegate to `tradeService.updateStatus(id,
req.status())` and return `ResponseEntity.ok(...)`. The status-only
sub-resource makes RBAC clean — you can authorise this path
independently of the full-update path.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `@PatchMapping("/{id}/status")` to `TradeController` taking `@PathVariable Long id`, the request body, and `@AuthenticationPrincipal Object principal`.
2. Either accept a `Map<String, String>` body or a tiny `StatusUpdate` record with `@NotBlank` + `@Pattern(regexp = "PENDING|MATCHED|UNMATCHED|DISPUTED|CANCELLED")`.
3. Extract the status string and call `service.updateStatus(id, status, actor)`.
4. Map the updated entity through `mapper.toResponse(...)` and return it directly (200 OK).
5. Verify: PATCH `{ "status": "MATCHED" }` returns 200 with the updated status; PATCH `{ "status": "FOOBAR" }` returns 400.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/TradeController.java`):

```java
@PatchMapping("/{id}/status")
@Operation(summary = "Update only the status field")
public TradeResponse updateStatus(@PathVariable Long id,
                                  @RequestBody Map<String, String> body,
                                  @AuthenticationPrincipal Object principal) {
    String status = body.get("status");
    return mapper.toResponse(service.updateStatus(id, status, String.valueOf(principal)));
}
```

</details>

**▶ Run the project — verify TICKET-ADV066 end-to-end**

PATCH the status sub-resource, then read the trade back to confirm only `status` changed.

```bash
curl -i -X PATCH http://localhost:8080/api/v1/trades/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"MATCHED"}'

curl -s http://localhost:8080/api/v1/trades/1 | jq '.status'

# Invalid status → 400
curl -i -X PATCH http://localhost:8080/api/v1/trades/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"FOOBAR"}'
```

**Observe:**

- PATCH returns 200 and the response body's `status` field is `MATCHED`.
- The follow-up GET also shows `MATCHED`; all other fields (quantity, price, side) are unchanged.
- Invalid status returns 400 with a validation message — anything else means the `@Pattern` constraint is missing.

---

### TICKET-ADV067 — DELETE /api/v1/trades/{id} (soft delete)

**Goal:** Logically delete a trade so it disappears from list responses while remaining in the database for audit.

**What**
- `TradeController.delete(...)` mapped to `DELETE /api/v1/trades/{id}` returning `ResponseEntity.noContent().build()`, plus a Liquibase changeset adding `deleted_at TIMESTAMP` to `trades` and a class-level `@SQLRestriction("deleted_at IS NULL")` on the `Trade` entity.

**Why**
- Soft delete keeps the Day 4 Envers history intact and lets the Day 9 compliance/audit endpoints still walk deleted rows — a hard `DELETE FROM` would orphan every downstream audit query.

**Observe**
- `curl -i -X DELETE ...` returns `HTTP/1.1 204 No Content`; the follow-up `GET /api/v1/trades` excludes the row, but `SELECT id, deleted_at FROM trades WHERE id = ?` still shows it with a non-NULL timestamp.

**Done when:**
- `DELETE /api/v1/trades/{id}` returns HTTP 204 No Content with an empty body.
- After delete, `GET /api/v1/trades` no longer returns the deleted row.
- A row physically remains in the `trades` table with `deleted_at` populated — verify with a direct SQL query.

<details>
<summary>Hint 1 — gentle direction</summary>

Do your delete, then immediately call your list endpoint. Is the trade
still there? If yes, the read side is not honouring soft-delete yet.
Where would a filter for "only non-deleted rows" live so it applies to
every query, not just the list endpoint?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

On the entity side, set a `deleted_at` timestamp instead of issuing a
`DELETE FROM`. To hide soft-deleted rows from all reads in one place,
look at Hibernate 6.3's `@SQLRestriction` annotation on the entity
class. The status code for a successful delete with no body is 204, not
200.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Controller: `@DeleteMapping("/{id}")` returns
`ResponseEntity.noContent().build()`. Service `softDelete(id)` finds the
entity (404 if missing), sets `deletedAt` to now, no explicit save
inside `@Transactional`. Add `@SQLRestriction("deleted_at IS NULL")` at
the class level on the `Trade` entity so every Hibernate-issued SELECT
silently filters deleted rows.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a Liquibase changeset that adds a nullable `deleted_at TIMESTAMP` column to `trades`.
2. Add `deletedAt` (OffsetDateTime) to the `Trade` entity and annotate the class with `@SQLRestriction("deleted_at IS NULL")` so reads silently skip soft-deleted rows.
3. Implement `TradeService.softDelete(id, actor)` inside `@Transactional` — find or throw 404, set `deletedAt = OffsetDateTime.now()`, rely on dirty-checking.
4. Add `@DeleteMapping("/{id}")` to the controller returning `ResponseEntity.noContent().build()`.
5. Verify: DELETE returns 204, follow-up GET excludes the row, but `SELECT * FROM trades` directly still shows the row with `deleted_at` populated.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/TradeController.java`):

```java
@DeleteMapping("/{id}")
@Operation(summary = "Soft delete (sets deleted_at)")
public ResponseEntity<Void> delete(@PathVariable Long id,
                                   @AuthenticationPrincipal Object principal) {
    service.softDelete(id, String.valueOf(principal));
    return ResponseEntity.noContent().build();
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/TradeService.java` — softDelete excerpt):

```java
public void softDelete(Long id, String actor) {
    Trade t = tradeRepo.findById(id)
            .orElseThrow(() -> new TradeNotFoundException("id=" + id));
    t.softDelete();
    tradeRepo.save(t);
    events.publish(new TradeEvent(UUID.randomUUID(), t.getTradeRef(),
            TradeEvent.EventType.TRADE_CANCELLED, Instant.now(), actor, null, null));
}
```

</details>

**▶ Run the project — verify TICKET-ADV067 end-to-end**

DELETE a trade, confirm the GET list excludes it, then prove the row physically remains.

```bash
curl -i -X DELETE http://localhost:8080/api/v1/trades/1

# Should NOT appear in the default list anymore
curl -s "http://localhost:8080/api/v1/trades" | jq '.items[] | select(.id == 1)'

# Direct SQL check — row still present with deleted_at populated
docker compose exec postgres psql -U reconx -d reconx \
  -c "SELECT id, trade_ref, deleted_at FROM trades WHERE id = 1;"
```

**Observe:**

- DELETE returns `HTTP/1.1 204 No Content` with an empty body.
- The follow-up jq filter returns nothing — the row is hidden by `@SQLRestriction`.
- The SQL query shows the row with a non-NULL `deleted_at` timestamp; if the row is gone, you issued a hard `DELETE FROM` instead of a soft delete.

---

### TICKET-ADV068 — POST /api/v1/recon/run

**Goal:** Trigger a reconciliation job asynchronously and return a job handle the client can poll.

**What**
- `ReconController.runRecon(...)` mapped to `POST /api/v1/recon/run` accepting a `@Valid ReconRunRequest`, generating a `UUID` jobId, and returning `ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "QUEUED"))` with a `Location` header pointing at the results endpoint.

**Why**
- Async job-handle semantics (202 + Location + jobId) are what Day 6's Kafka recon-job worker picks up and what Day 8's React dashboard polls — a synchronous 200 would time out under real recon volumes.

**Observe**
- `curl -i -X POST /api/v1/recon/run` returns `HTTP/1.1 202 Accepted`, the body shows `status: QUEUED` with a valid UUID jobId, and the application log shows a `recon job dispatched` line.

**Done when:**
- Posting a `ReconRequest` (from-date, to-date, optional counterpartyId) returns HTTP 202 Accepted, not 200.
- The response body contains a `jobId` (UUID string) and an initial `status` of `QUEUED`.
- A `Location` header points at the future results endpoint for that job id.

<details>
<summary>Hint 1 — gentle direction</summary>

A real reconciliation can take minutes. If the controller blocks on
the full result, the HTTP request will time out long before the job
finishes. What is the standard HTTP status code for "I have accepted
the work and started it; come back later"?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`ResponseEntity.accepted()` produces a 202. The service kicks the work
off — either on a `@Async` method or by enqueueing a job record — and
returns immediately with a generated job id. The `Location` header
points at the resource the client will poll in TICKET-ADV069.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Controller: `run(@RequestBody @Valid ReconRequest req)` then call
`reconService.triggerAsync(req)` to get a `String jobId`, then return
`ResponseEntity.accepted()` with a `Location` header pointing at
`/api/v1/recon/jobs/{jobId}/results` and a body wrapping the jobId and
an initial `QUEUED` status. Both `ReconRequest` and the response are
records with validation annotations.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create a `ReconRunRequest` record with `@NotNull` on `from`/`to` and an optional `counterpartyId`.
2. Create `ReconController` annotated `@RestController @RequestMapping("/v1/recon")` with `@SecurityRequirement(name = "bearerAuth")`.
3. Add `@PostMapping("/run")` accepting `@Valid @RequestBody ReconRunRequest req`.
4. Generate a `UUID.randomUUID().toString()` as the jobId (full impl would write a row to `recon_jobs` and a worker would pick it up).
5. Return `ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "QUEUED"))` — 202, not 200.
6. Verify with `curl -X POST` — confirm HTTP 202 and a valid UUID in `jobId`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/dto/ReconRunRequest.java`):

```java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReconRunRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        Long counterpartyId
) {}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/ReconController.java`):

```java
@PostMapping("/run")
@Operation(summary = "Trigger a reconciliation job (async)")
public ResponseEntity<Map<String, String>> runRecon(@Valid @RequestBody ReconRunRequest req) {
    String jobId = UUID.randomUUID().toString();
    // In the full impl this writes a row to recon_jobs and a worker picks it up.
    return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "QUEUED"));
}
```

</details>

**▶ Run the project — verify TICKET-ADV068 end-to-end**

POST a recon-run request and confirm the 202 Accepted response with a jobId.

```bash
curl -i -X POST http://localhost:8080/api/v1/recon/run \
  -H "Content-Type: application/json" \
  -d '{"from":"2026-03-01","to":"2026-03-31"}'
```

**Observe:**

- Response status is `HTTP/1.1 202 Accepted`, not 200.
- Response body contains a `jobId` (a valid UUID) and `status: QUEUED`.
- Application log shows a line like `recon job dispatched: jobId=...` — if missing, the service did not actually enqueue the work.

---

### TICKET-ADV069 — GET /api/v1/recon/jobs/{jobId}/results

**Goal:** Return the paginated reconciliation breaks for a specific job.

**What**
- `ReconController.results(...)` mapped to `GET /api/v1/recon/jobs/{jobId}/results` taking `@PageableDefault(size = 50) Pageable`, querying `ReconBreakRepository`, and returning a `PagedResponse<ReconResultResponse>` reusing the ADV063 envelope.

**Why**
- This is the polling endpoint paired with ADV068's job handle, and the RBAC matrix (VIEWER + RECON_ANALYST + ADMIN allowed, TRADER denied) is the exact rule ADV074's `SecurityFilterChain` and ADV077's MockMvc test exercise.

**Observe**
- `?size=50` is honoured in the response envelope; a TRADER token returns `HTTP/1.1 403 Forbidden` once ADV074 lands, while a 403 today (before Workshop 5B) just means security is off entirely.

**Done when:**
- `GET /api/v1/recon/jobs/{jobId}/results` returns a `PagedResponse<ReconResultResponse>` body.
- `?page=0&size=50` paging parameters are respected.
- Three roles (VIEWER, RECON_ANALYST, ADMIN) can read this endpoint; TRADER cannot.

<details>
<summary>Hint 1 — gentle direction</summary>

This endpoint is read-heavy — auditors and analysts both look at
results. Which role buckets should be allowed to read recon results?
Sketch the role-to-method matrix on paper before you wire authorisation.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Reuse the `PagedResponse.from(Page)` helper from TICKET-ADV063. The service
queries `ReconBreakRepository` filtered by `jobId` and accepts a
`Pageable`. Authorise this path for `VIEWER`, `RECON_ANALYST`, and
`ADMIN`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Method signature: `results(@PathVariable String jobId,
@PageableDefault(size = 50) Pageable pageable)` returning
`ResponseEntity<PagedResponse<ReconResultResponse>>`. The
`@PreAuthorize` expression lists the three roles. The service returns
`Page<ReconResultResponse>` which the controller wraps in
`PagedResponse.from(...)`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `@GetMapping("/jobs/{jobId}/results")` to `ReconController`.
2. Accept `@PathVariable String jobId` and (optionally) `@PageableDefault(size = 50) Pageable pageable`.
3. Query `ReconBreakRepository` for breaks belonging to that job (the trainer-stub returns all open breaks).
4. URL-level authorisation lives in `SecurityConfig` — `/v1/recon/**` is restricted to `RECON_ANALYST` and `ADMIN` (VIEWER added if your matrix requires it).
5. Return the list (or a `PagedResponse` wrapper if you went the paginated route).
6. Curl with a VIEWER/RECON_ANALYST/ADMIN token — confirm 200; TRADER token — confirm 403.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/ReconController.java`):

```java
@GetMapping("/jobs/{jobId}/results")
@Operation(summary = "Get results for a recon job")
public List<ReconBreak> results(@PathVariable String jobId) {
    // The trainer-copy stub returns all current open breaks.
    return breaks.findAll();
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/security/SecurityConfig.java` — relevant rule):

```java
.requestMatchers("/v1/recon/**").hasAnyRole("RECON_ANALYST","ADMIN")
```

</details>

**▶ Run the project — verify TICKET-ADV069 end-to-end**

GET the breaks for a specific recon job and confirm pagination metadata.

```bash
# Use a jobId from the ADV068 response, or any string while the trainer-stub returns all open breaks
curl -s "http://localhost:8080/api/v1/recon/jobs/00000000-0000-0000-0000-000000000001/results?page=0&size=50" | jq
```

**Observe:**

- Response is 200 with a JSON list (or `PagedResponse` envelope) of break rows.
- Page size of 50 is respected; each entry has `id`, `status`, and break metadata fields.
- A 403 here means the role you are calling with is not in the recon path's allow-list — switch tokens once Workshop 5B lands.

---

### TICKET-ADV070 — PUT /api/v1/recon/results/{id}/resolve

**Goal:** Mark a single reconciliation break as resolved with a mandatory analyst note.

**What**
- `ReconController.resolve(...)` mapped to `PUT /api/v1/recon/results/{id}/resolve` accepting a `@NotBlank @Size(max = 500) note`, setting `status=RESOLVED`, `resolvedAt=now`, `resolutionNote=note` on the break inside `@Transactional`, and returning the updated `ReconResultResponse`.

**Why**
- Atomic resolution (all three fields move as a unit) is what the Day 4 Envers audit trail captures as a single revision and what Day 9's compliance reports key off — partial fields would corrupt the audit log.

**Observe**
- First call returns 200 with `status: RESOLVED` and a non-null `resolvedAt`; an empty note returns `HTTP/1.1 400 Bad Request` because the `@NotBlank` constraint fires before the controller body runs.

**Done when:**
- `PUT /api/v1/recon/results/{id}/resolve` with a `{ "note": "..." }` body returns 200 and the updated break.
- The break's `status` becomes `RESOLVED`, `resolvedAt` is now, and `resolutionNote` carries the analyst's text.
- Empty or missing notes return 400.

<details>
<summary>Hint 1 — gentle direction</summary>

Resolution sets several fields at once: status, timestamp, note. Can
the caller resolve "half" a break by submitting only some fields? If
the answer is "no, resolution is atomic", which verb communicates that
better — PUT or PATCH?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Make a `ResolutionRequest` record with `@NotBlank @Size(max = 500)
String note`. Use `@PutMapping` because resolving is an atomic
operation (multiple fields change together as a unit). The service
loads the break, sets the three fields, and returns the mapped DTO
inside a `@Transactional` boundary.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Controller: `resolve(@PathVariable Long id, @RequestBody @Valid
ResolutionRequest req)`. Service: find-or-throw, set status to
`RESOLVED`, set `resolvedAt` to `OffsetDateTime.now()`, set
`resolutionNote` from the request. Authorise for `RECON_ANALYST` and
`ADMIN` only — TRADER and VIEWER must not touch this.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add `@PutMapping("/results/{id}/resolve")` to `ReconController`.
2. Accept `@PathVariable Long id` and the resolution body (a `Map<String,String>` or a `ResolutionRequest` record with `@NotBlank @Size(max=500) String note`).
3. Look up the `ReconBreak` by id; throw `TradeNotFoundException` (404) when missing.
4. Call `rb.resolve(note)` — domain method that sets `status=RESOLVED`, `resolvedAt=now`, `resolutionNote=note` atomically.
5. Save and return the persisted entity wrapped in `ResponseEntity.ok(...)`.
6. Verify: PUT with `{ "note": "Confirmed by counterparty." }` returns 200 with `status=RESOLVED`; empty note returns 400.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/ReconController.java`):

```java
@PutMapping("/results/{id}/resolve")
@Operation(summary = "Mark a recon break as RESOLVED with a note")
public ResponseEntity<ReconBreak> resolve(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
    ReconBreak rb = breaks.findById(id)
            .orElseThrow(() -> new TradeNotFoundException("recon_break " + id));
    rb.resolve(body.getOrDefault("note", "manually resolved"));
    return ResponseEntity.ok(breaks.save(rb));
}
```

</details>

**▶ Run the project — verify TICKET-ADV070 end-to-end**

PUT a resolution note on an open break and confirm status flips to RESOLVED.

```bash
curl -i -X PUT http://localhost:8080/api/v1/recon/results/1/resolve \
  -H "Content-Type: application/json" \
  -d '{"note":"Confirmed via counterparty email on 2026-03-16."}'

# Empty note → 400
curl -i -X PUT http://localhost:8080/api/v1/recon/results/1/resolve \
  -H "Content-Type: application/json" \
  -d '{"note":""}'
```

**Observe:**

- First call returns 200; response body shows `status: RESOLVED`, a non-null `resolvedAt`, and the note echoed in `resolutionNote`.
- Second call returns `HTTP/1.1 400 Bad Request` because `@NotBlank` on the note rejected the empty string.
- A second call on the same id returning 200 again is fine — resolving an already-resolved break should be idempotent.

---

### TICKET-ADV071 — GET /api/v1/audit/trades/{tradeRef}

**Goal:** Expose Envers-backed revision history for a trade as a list of JSON snapshots.

**What**
- `AuditController` mapped to `/api/v1/audit` with `GET /trades/{tradeRef}` injecting `AuditLogRepository` (or Envers' `AuditReader`) and returning the revision list ordered oldest-first via `findByTradeRefOrderByEventTimestampAsc(...)`.

**Why**
- Envers was wired in Day 4 for exactly this surface — Day 9's compliance day reuses the same `AuditLogEntry` payload for regulatory exports, so the JSON shape (`revisionId`, `revisionTimestamp`, `revisionType`, `changedBy`, `snapshot`) needs to lock in here.

**Observe**
- Admin-authenticated GET returns a JSON array starting with an `ADD` revision; an empty array for a known-edited trade means the Day 4 Envers `_AUD` tables are not populating — re-check the Day 4 `@Audited` annotations.

**Done when:**
- `GET /api/v1/audit/trades/{tradeRef}` returns an array of revisions ordered oldest-first.
- Each entry includes `revisionId`, `revisionTimestamp`, `revisionType` (`ADD`/`MOD`/`DEL`), `changedBy`, and a full `snapshot` of the trade at that revision.
- A trade that has never been edited still shows the initial `ADD` revision from creation.

<details>
<summary>Hint 1 — gentle direction</summary>

Day 4 wired Hibernate Envers and you should now have `_AUD` tables in
the database. The work here is presentation — turning each row of
`trades_aud` into a JSON envelope. What information would a compliance
officer need to see at a glance?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Inject Envers' `AuditReader` (you can get one from
`AuditReaderFactory.get(entityManager)`). Use
`AuditReader.createQuery().forRevisionsOfEntity(Trade.class, false,
true)` and filter by `tradeRef`. Map each row to a `TradeRevision`
record before returning.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`AuditController` delegates to `AuditService.findRevisions(tradeRef)`
which returns `List<TradeRevision>`. The service queries the audit
reader, iterates the returned rows (entity, revisionEntity,
RevisionType), and builds the record. Authorise for `ADMIN`,
`RECON_ANALYST`, and `VIEWER` — the read-only role bucket.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `AuditController` annotated `@RestController @RequestMapping("/v1/audit")` with `@SecurityRequirement(name = "bearerAuth")`.
2. Inject `AuditLogRepository` (or `AuditReader` for an Envers-driven path).
3. Add `@GetMapping("/trades/{tradeRef}")` returning `List<AuditLogEntry>`.
4. Delegate to `auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef)` so revisions return oldest-first.
5. Authorise read-only roles via `SecurityConfig` (`/v1/audit/**` → `RECON_ANALYST`, `ADMIN`, optionally `VIEWER`).
6. Verify against a seeded `tradeRef` — the JSON array must include the initial ADD revision plus any subsequent MOD revisions.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/AuditController.java`):

```java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /api/v1/audit/trades/{tradeRef}
 * GET /api/v1/audit/trades/{tradeRef}/events
 */
@RestController
@RequestMapping("/v1/audit")
@Tag(name = "audit")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditLogRepository auditRepo;

    public AuditController(AuditLogRepository auditRepo) { this.auditRepo = auditRepo; }

    @GetMapping("/trades/{tradeRef}")
    @Operation(summary = "Get audit history for a trade (by tradeRef)")
    public List<AuditLogEntry> history(@PathVariable String tradeRef) {
        return auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);
    }

    @GetMapping("/trades/{tradeRef}/events")
    @Operation(summary = "Stream of all Kafka-sourced events for a trade")
    public List<AuditLogEntry> events(@PathVariable String tradeRef) {
        return auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV071 end-to-end**

GET the audit history for a seeded trade (note: this needs Workshop 5B's JWT working, so revisit once ADV072 lands).

```bash
# Once JWT auth is in place, log in as admin first
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq -r '.token')

# Pull audit history with the admin token
curl -s http://localhost:8080/api/v1/audit/trades/TRD-20260315-0001 \
  -H "Authorization: Bearer $TOKEN" | jq

# Same endpoint without a token (or with a TRADER token) → 403
curl -i http://localhost:8080/api/v1/audit/trades/TRD-20260315-0001
```

**Observe:**

- Admin-authenticated GET returns a JSON array of revision rows — at minimum the initial `ADD` revision.
- Non-admin or unauthenticated call returns `HTTP/1.1 403 Forbidden` (or 401 if no token at all).
- An empty array for a trade you know was edited means Envers `_AUD` tables are not populating — re-check Day 4.

---

### Workshop 5B — JWT + RBAC

This block adds an authentication endpoint, a stateless filter that
populates the security context from a Bearer token, and the
`SecurityFilterChain` that enforces the RBAC matrix you have been
sprinkling `@PreAuthorize` annotations against.

---

### TICKET-ADV072 — JWT issuance on /api/auth/login

**Goal:** Authenticate a user with username/password and return a signed JWT containing the user's roles.

**What**
- `AuthController.login(...)` mapped to `POST /api/auth/login` plus a `JwtTokenProvider` that signs claims (`sub`, `role`, `iat`, `exp`) using `Keys.hmacShaKeyFor(secret)` where the secret comes from `${APP_JWT_SECRET:dev-default}` — never a hard-coded literal.

**Why**
- This is the gate every other protected endpoint funnels through, and the BCrypt user seed planted in ADV017 is what makes the password check succeed — Day 6 Kafka consumers and Day 8 React fetches both attach the token this issues.

**Observe**
- Valid login returns 200 with a `{token, tokenType: "Bearer", expiresInSeconds, role}` body; pasting the token into jwt.io decodes cleanly with the configured secret; wrong password returns `HTTP/1.1 401 Unauthorized`, not 200 with a token.

**Done when:**
- `POST /api/auth/login` with `{ "username": "trader@db.com", "password": "trader123" }` returns 200 and a `{ token, tokenType: "Bearer", expiresIn }` body.
- Pasting the returned token into [jwt.io](https://jwt.io/) decodes cleanly and shows `sub`, `roles`, `iat`, and `exp` claims.
- Wrong credentials return 401, not 200 with a token.

<details>
<summary>Hint 1 — gentle direction</summary>

Where is your JWT secret defined? Open the file. If you can read the
literal value of the secret in source code, ask yourself: if I pushed
this to git, could anyone with read access to the repo issue valid
ReconX tokens? Secrets do not belong in committed config.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Look at Spring's property placeholder syntax for environment variables
with defaults: `${VAR_NAME:dev-default}`. For the signing key, use
`io.jsonwebtoken.security.Keys.hmacShaKeyFor(...)` over a stable secret
string (not `Keys.secretKeyFor(...)` which generates a fresh random key
each boot). The Spring `AuthenticationManager` is the workhorse that
runs `UsernamePasswordAuthenticationToken` through the configured
`UserDetailsService` plus `PasswordEncoder`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Build a `JwtTokenProvider` component that loads the secret with
`@Value("${app.jwt.secret}")` and validity-ms with a default. Expose
`generateToken(Authentication)` that puts the principal name in `sub`,
the granted authorities under a `roles` claim, sets `iat` and `exp`,
and signs with the HS256 key. Expose `parseClaims(String)` for the
filter. Build `LoginRequest` and `LoginResponse` records.
`AuthController.login` calls `authManager.authenticate(...)` then
hands the resulting `Authentication` to the token provider.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Externalise the secret in `application.yml` under `reconx.security.jwt.secret`, `expiration-minutes`, `issuer` — referencing an env var with a dev default.
2. Build `JwtTokenProvider` annotated `@Component`; inject the three properties; build an `SecretKey` via `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` in the constructor.
3. Implement `generate(email, role)` that sets `subject=email`, `issuer`, `issuedAt`, `expiration`, `claims={"role":role}`, and signs with the key (jjwt 0.12 API).
4. Implement `parse(token)` that verifies the signature, requires the configured issuer, and returns `Claims`.
5. Create `LoginRequest` (`@Email @NotBlank email`, `@NotBlank password`) and `LoginResponse` records.
6. Build `AuthController` with `@PostMapping("/login")` — look up the `AppUser`, verify with `PasswordEncoder.matches`, throw `InvalidTradeException` on mismatch, otherwise call `jwt.generate(...)` and return the token envelope.
7. Verify in jwt.io with the configured secret that the decoded payload shows `sub`, `role`, `iat`, `exp`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/security/JwtTokenProvider.java`):

```java
package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * ============================================================================
 * JwtTokenProvider (jjwt 0.12.x API)
 *
 * WHAT:    Generates + validates HS256-signed JWTs.
 * HOW:     Subject = email. Role goes into a custom "role" claim that
 *          {@link JwtAuthenticationFilter} turns into a GrantedAuthority.
 * WHY:     Self-contained (no DB hit per request) and stateless (no session).
 * OBSERVE: Decode any token at jwt.io with the configured secret.
 * ============================================================================
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMinutes;
    private final String issuer;

    public JwtTokenProvider(@Value("${reconx.security.jwt.secret}") String secret,
                            @Value("${reconx.security.jwt.expiration-minutes}") long expirationMinutes,
                            @Value("${reconx.security.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
        this.issuer = issuer;
    }

    public String generate(String email, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationMinutes * 60);
        return Jwts.builder()
                .subject(email)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of("role", role))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expirationSeconds() { return expirationMinutes * 60; }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/AuthController.java`):

```java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.LoginRequest;
import com.dbtraining.reconx.dto.LoginResponse;
import com.dbtraining.reconx.exception.InvalidTradeException;
import com.dbtraining.reconx.repository.AppUserRepository;
import com.dbtraining.reconx.repository.entity.AppUser;
import com.dbtraining.reconx.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/auth/login
 *
 * Verifies BCrypt password, returns a JWT carrying the user's role.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtTokenProvider jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for a JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        AppUser u = users.findByEmail(req.email())
                .orElseThrow(() -> new InvalidTradeException("Invalid credentials"));
        if (!u.getEnabled() || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new InvalidTradeException("Invalid credentials");
        }
        String token = jwt.generate(u.getEmail(), u.getRole());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwt.expirationSeconds(), u.getRole()));
    }
}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/dto/LoginRequest.java`):

```java
package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/login body. */
public record LoginRequest(@Email @NotBlank String email,
                           @NotBlank String password) {}
```

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/dto/LoginResponse.java`):

```java
package com.dbtraining.reconx.dto;

/** JWT envelope returned to clients. */
public record LoginResponse(String token, String tokenType, long expiresInSeconds, String role) {}
```

</details>

**▶ Run the project — verify TICKET-ADV072 end-to-end**

POST credentials to `/api/auth/login` and decode the returned JWT.

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq

# Wrong credentials → 401
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"wrong"}'
```

**Observe:**

- Valid login returns 200 with a body containing `token`, `tokenType: "Bearer"`, `expiresInSeconds`, and `role`.
- Paste the token into [jwt.io](https://jwt.io/) — the payload shows `sub` (email), `role`, `iat`, and `exp` claims, signed with HS256.
- Wrong password returns `HTTP/1.1 401 Unauthorized` — if you get 200 with a token, BCrypt verification is short-circuiting.

---

### TICKET-ADV073 — JwtAuthenticationFilter

**Goal:** Read the Bearer token off each request, validate it, and populate the Spring Security context.

**What**
- `JwtAuthenticationFilter extends OncePerRequestFilter` that pulls the `Authorization: Bearer ...` header, calls `JwtTokenProvider.parse(token)`, maps the `role` claim to a `SimpleGrantedAuthority("ROLE_" + role)`, and sets a `UsernamePasswordAuthenticationToken` on `SecurityContextHolder` — failing closed by clearing the context on `JwtException`.

**Why**
- This is the bridge between the ADV072 token and ADV074's `@PreAuthorize` checks — every protected request on Day 6, 7, 8, and 9 routes through this filter, so it has to be idempotent (`OncePerRequestFilter`) and never throw.

**Observe**
- A request with a valid token returns 200 and `SecurityContextHolder.getContext().getAuthentication()` is populated inside the controller; a bogus token returns 401 (filter caught `JwtException` silently); a 500 stack trace means the filter is re-throwing instead of failing closed.

**Done when:**
- A request carrying a valid `Authorization: Bearer ...` header arrives at the controller with `SecurityContextHolder.getContext().getAuthentication()` populated.
- A request with a missing, malformed, or expired token still flows through the chain but lands at the controller with no authentication (so downstream rules return 401).
- The filter runs exactly once per request, even on async dispatch.

<details>
<summary>Hint 1 — gentle direction</summary>

A servlet filter that should run once per request has a base class in
Spring that handles re-entry correctly. Which base class is it? And
once you have parsed and validated the token, how do you actually tell
the rest of Spring Security "this is the authenticated user"?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Extend `OncePerRequestFilter` (not `GenericFilterBean`). The relevant
method to override is `doFilterInternal`. After parsing claims, build a
`UsernamePasswordAuthenticationToken` with principal = subject,
credentials = null, authorities = roles mapped to
`SimpleGrantedAuthority`. Set it on
`SecurityContextHolder.getContext()`. Whatever path you take through
the try/catch, you must still call `chain.doFilter(req, res)`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Pull the `Authorization` header. If it starts with `Bearer `, slice
the prefix off and call `tokenProvider.parseClaims(token)` in a try
block. Map the `roles` claim (a `List<String>`) to authorities, build
the auth token, set it on the context. On `JwtException`, clear the
context — fail closed, do not throw. Always call
`chain.doFilter(req, res)` at the end so the request continues down
the chain.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `JwtAuthenticationFilter` extending `OncePerRequestFilter`, annotated `@Component`.
2. Inject `JwtTokenProvider` via constructor.
3. Override `doFilterInternal(req, res, chain)`. Pull `Authorization` header; if absent or not Bearer, skip to `chain.doFilter(...)`.
4. Slice off the `Bearer ` prefix, call `provider.parse(token)` inside a try block.
5. Read `claims.getSubject()` for the email and `claims.get("role")` for the role; build a `SimpleGrantedAuthority("ROLE_" + role)` list.
6. Build `UsernamePasswordAuthenticationToken(email, null, authorities)`, attach `WebAuthenticationDetailsSource().buildDetails(req)`, set on `SecurityContextHolder`.
7. On `JwtException` clear the context (fail closed). Always call `chain.doFilter(req, res)` so the request continues.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/security/JwtAuthenticationFilter.java`):

```java
package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter
 *
 * Reads Authorization: Bearer <token>, parses it, sets SecurityContext.
 * Errors are not rendered here — Spring's exception handler converts
 * missing/expired tokens into a 401 once the request reaches a protected
 * endpoint.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider provider;

    public JwtAuthenticationFilter(JwtTokenProvider provider) { this.provider = provider; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = provider.parse(token);
                String email = claims.getSubject();
                String role = (String) claims.get("role");
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV073 end-to-end**

Hit a protected endpoint without a token (401), with a valid token (200), and with a junk token (401).

```bash
# No token → 401
curl -i http://localhost:8080/api/v1/trades

# With valid token → 200
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq -r '.token')
curl -i http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TOKEN"

# Bogus / expired token → 401
curl -i http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer not.a.real.token"
```

**Observe:**

- Anonymous request returns `HTTP/1.1 401 Unauthorized`.
- Valid token returns 200 with the trades list — `SecurityContextHolder` was populated by the filter.
- Bogus token also returns 401; the filter caught `JwtException`, cleared the context, and let the chain bounce the request — if you see a 500 stack trace, the filter is re-throwing instead of failing closed.

---

### TICKET-ADV074 — SecurityFilterChain + RBAC

**Goal:** Wire the filter into the chain and enforce URL-level RBAC for trades, recon, and audit endpoints.

**What**
- `SecurityConfig` exposing a `SecurityFilterChain` bean that disables CSRF, sets `SessionCreationPolicy.STATELESS`, permits `/api/auth/login` and `/actuator/health`, registers `JwtAuthenticationFilter` via `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`, and binds `/api/v1/trades/**`, `/api/v1/recon/**`, `/api/v1/audit/**` to the role buckets seeded in ADV017.

**Why**
- This is where the RBAC matrix (ADMIN/TRADER/VIEWER/RECON_ANALYST) actually fires — without `STATELESS` + CSRF disabled, every POST in the Testcontainers test ADV078 and the Day 8 React frontend would get an anonymous 403 instead of a clean 401.

**Observe**
- VIEWER POST returns 403, TRADER DELETE returns 403, RECON_ANALYST resolve returns 200; if a valid POST returns 403 unexpectedly, CSRF is still on; if a `hasRole("ROLE_ADMIN")` rule matches nothing, the manual `ROLE_` prefix is double-applied.

**Done when:**
- The `SecurityFilterChain` bean disables CSRF, sets session policy to STATELESS, permits `/api/auth/login` and `/actuator/health`, and authenticates everything else.
- The JWT filter runs before `UsernamePasswordAuthenticationFilter` in the chain.
- VIEWER cannot POST, TRADER cannot DELETE, RECON_ANALYST can resolve breaks. The matrix is enforced at the URL layer (filter chain) and reinforced at the method layer (`@PreAuthorize`).

<details>
<summary>Hint 1 — gentle direction</summary>

You will see two common pitfalls today. First: POST returns 403 even
though the token is valid. Think about what Spring Security defaults
to expecting on state-changing requests in non-stateless mode. Second:
`hasRole("ROLE_ADMIN")` matches nothing. Spring auto-prefixes — do you
want to give it the prefix yourself?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

In the lambda passed to `http.authorizeHttpRequests(...)`, use
`requestMatchers(HttpMethod.POST, "/api/v1/trades")` and chain
`.hasAnyRole("TRADER", "ADMIN")`. Disable CSRF with
`http.csrf(AbstractHttpConfigurer::disable)`. Set session creation
policy to `STATELESS`. Use `addFilterBefore(jwtFilter,
UsernamePasswordAuthenticationFilter.class)`. Annotate the config
class with `@EnableMethodSecurity` so `@PreAuthorize` is honoured.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

A `@Configuration` class with `@EnableMethodSecurity` declares three
beans: a `SecurityFilterChain` taking `HttpSecurity` and the token
provider, a BCrypt `PasswordEncoder`, and an `AuthenticationManager`
sourced from `AuthenticationConfiguration`. The chain disables CSRF,
sets STATELESS, permits login + health + swagger, then matches
POST/PUT/PATCH on trades to TRADER and ADMIN, DELETE on trades to
ADMIN only, POST and PUT on recon to RECON_ANALYST and ADMIN,
anyRequest authenticated. Finally adds the JWT filter before
`UsernamePasswordAuthenticationFilter`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `SecurityConfig` annotated `@Configuration @EnableMethodSecurity`.
2. Expose a `PasswordEncoder` bean returning `new BCryptPasswordEncoder()`.
3. Expose a `SecurityFilterChain` bean taking `HttpSecurity` and the `JwtAuthenticationFilter`.
4. On the chain: disable CSRF, set session policy to STATELESS, permit `/auth/login`, `/actuator/health/**`, swagger and H2 paths.
5. Add method+path RBAC rules — GET trades to all roles; POST/PUT/PATCH trades to TRADER+ADMIN; DELETE trades to ADMIN; `/v1/recon/**` and `/v1/audit/**` to RECON_ANALYST+ADMIN; `anyRequest().authenticated()`.
6. Add `.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` so the JWT filter runs first.
7. Boot and probe with curl: no token → 401; VIEWER POST → 403; TRADER POST → 201.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/security/SecurityConfig.java`):

```java
package com.dbtraining.reconx.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ============================================================================
 * Stateless security filter chain wiring JWT filter
 * RBAC: HTTP-method + path level role rules
 *                Roles: ADMIN, TRADER, VIEWER, RECON_ANALYST
 *
 * NOTE: `/api` context-path is set in application.yml, so paths here
 *       are relative to that (e.g. /v1/trades resolves to /api/v1/trades).
 * ============================================================================
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/auth/login",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/h2/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET,    "/v1/trades/**").hasAnyRole("VIEWER","TRADER","RECON_ANALYST","ADMIN")
                .requestMatchers(HttpMethod.POST,   "/v1/trades").hasAnyRole("TRADER","ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/v1/trades/**").hasAnyRole("TRADER","ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/v1/trades/**").hasRole("ADMIN")
                .requestMatchers("/v1/recon/**").hasAnyRole("RECON_ANALYST","ADMIN")
                .requestMatchers("/v1/audit/**").hasAnyRole("RECON_ANALYST","ADMIN")
                .anyRequest().authenticated()
            )
            .headers(h -> h.frameOptions(f -> f.disable()))   // for /h2 dev console
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV074 end-to-end**

Run the RBAC matrix: log in as VIEWER, attempt a write, expect 403; switch to TRADER, expect 201.

```bash
# VIEWER attempts a POST → 403
VIEWER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"viewer@db.com","password":"viewer123"}' | jq -r '.token')
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $VIEWER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0002","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}'

# TRADER attempts the same POST → 201
TRADER_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader@db.com","password":"trader123"}' | jq -r '.token')
curl -i -X POST http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TRADER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tradeRef":"TRD-20260315-0003","instrumentId":1,"counterpartyId":1,"assetClass":"EQUITY","side":"BUY","quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}'
```

**Observe:**

- VIEWER POST returns `HTTP/1.1 403 Forbidden` — the URL-level rule blocked it before the controller.
- TRADER POST returns `HTTP/1.1 201 Created` with a `Location` header.
- A 401 instead of 403 means the JWT filter didn't authenticate the token at all — check the `Authorization` header and the filter ordering.

---

### Workshop 5C — MockMvc + Testcontainers + versioning

Two tiers of tests. `@WebMvcTest` slice tests prove the HTTP contract
in isolation; `@SpringBootTest` plus Testcontainers proves the wiring
end-to-end against a real Postgres. You will write both, plus a
migration sanity check and an API-versioning exercise.

---

### TICKET-ADV075 — MockMvc: authenticated create returns 201

**Goal:** Write a `@WebMvcTest` that posts a valid trade as a TRADER and asserts the 201 response with a `Location` header.

**What**
- `TradeControllerWebMvcTest` annotated `@WebMvcTest(TradeController.class)` with `@MockBean TradeService`, a `validRequest()` helper, and a `testCreateTrade_authenticated_returns201` method annotated `@WithMockUser(roles = "TRADER")` that POSTs with `.with(csrf())` and asserts 201 + `Location` + `jsonPath("$.id").value(42)`.

**Why**
- Slice tests are the fast feedback loop — they run without Docker or Postgres, so CI can fail fast on a contract regression before the heavier ADV078 Testcontainers test even spins up.

**Observe**
- `./mvnw test -Dtest=TradeControllerWebMvcTest#testCreateTrade_authenticated_returns201` is green; `UnsatisfiedDependencyException` on a `JpaRepository` means the controller is autowiring the repo directly instead of going through the service.

**Done when:**
- The test class is annotated `@WebMvcTest(TradeController.class)` and uses `@MockBean` for `TradeService`.
- The test method runs with `@WithMockUser(roles = "TRADER")` and includes a CSRF token postprocessor on the request.
- Assertions cover status 201, `Location` containing the new id, and JSON body fields (`$.id`, `$.tradeRef`).

<details>
<summary>Hint 1 — gentle direction</summary>

`@WebMvcTest` is a slice — it does not load JPA. So if your controller
asks Spring to inject a `JpaRepository` directly, the slice will fail
with `UnsatisfiedDependencyException`. Where should the repository
dependency live, and what should you mock at the slice boundary?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `@WebMvcTest(TradeController.class)` to load only that controller.
Mock the service with `@MockBean`. Build the request with
`MockMvcRequestBuilders.post(...)`, set
`contentType(MediaType.APPLICATION_JSON)`, serialise the request DTO
with `ObjectMapper`, and chain
`.with(SecurityMockMvcRequestPostProcessors.csrf())`. Stub the service
with `when(tradeService.create(any())).thenReturn(...)`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Inside the test, set up the mock return, build the request with CSRF,
then chain assertions: `status().isCreated()`, `header().string(
"Location", containsString("/api/v1/trades/42"))`, and a couple of
`jsonPath` checks against `$.id` and `$.tradeRef`. The role on
`@WithMockUser` is `"TRADER"` (Spring adds the `ROLE_` prefix for
you).

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `TradeControllerWebMvcTest` annotated `@WebMvcTest(TradeController.class)`.
2. `@Autowire` `MockMvc` and `ObjectMapper`; `@MockBean` the `TradeService`.
3. Build a `validRequest()` helper returning a fully-populated `TradeRequest`.
4. Stub the service: `when(tradeService.create(any())).thenReturn(new TradeResponse(42L, ...))`.
5. Annotate the test method `@WithMockUser(roles = "TRADER")` — Spring prefixes with `ROLE_`.
6. Perform the POST with `MediaType.APPLICATION_JSON`, the serialised body, and `.with(csrf())` postprocessor.
7. Assert `status().isCreated()`, `header().string("Location", containsString("/api/v1/trades/42"))`, and `jsonPath("$.id").value(42)`.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/controller/TradeControllerWebMvcTest.java`):

```java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
class TradeControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private TradeService tradeService;

    private TradeRequest validRequest() {
        // Field order matches the current TradeRequest record:
        // (tradeRef, instrumentId, counterpartyId, assetClass, side, quantity, price, tradeDate).
        // tradeRef regex: ^[A-Z]{3}-\d{8}-\d{4}$. Status is NOT a request field — it is set server-side.
        return new TradeRequest(
                "TRD-20260315-9999",
                1L,
                1L,
                "EQUITY",
                "BUY",
                new BigDecimal("100.0000"),
                new BigDecimal("245.50"),
                LocalDate.now());
    }

    @Test
    @WithMockUser(roles = "TRADER")
    void testCreateTrade_authenticated_returns201() throws Exception {
        // Field order matches the current TradeResponse record:
        // (id, tradeRef, instrumentId, instrumentSymbol, counterpartyId, counterpartyName,
        //  assetClass, side, quantity, price, tradeDate, status, createdAt, modifiedAt).
        Instant now = Instant.now();
        when(tradeService.create(any())).thenReturn(
                new TradeResponse(
                        42L,
                        "TRD-20260315-9999",
                        1L,
                        "SAP.DE",
                        1L,
                        "Apex Brokers Inc",
                        "EQUITY",
                        "BUY",
                        new BigDecimal("100.0000"),
                        new BigDecimal("245.50"),
                        LocalDate.now(),
                        "PENDING",
                        now,
                        now));

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/trades/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.tradeRef").value("TRD-20260315-9999"));
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV075 end-to-end**

Run the MockMvc slice test in isolation — no Postgres or full Boot context needed.

```bash
./mvnw -pl backend test -Dtest=TradeControllerWebMvcTest#testCreateTrade_authenticated_returns201
```

**Observe:**

- Test passes green; Surefire reports `Tests run: 1, Failures: 0, Errors: 0`.
- The slice loads only `TradeController` — no `DataSource` or JPA in the context (so no Docker required for this test).
- Failure with `UnsatisfiedDependencyException` means the controller is autowiring a `JpaRepository` directly; push that dependency behind the service so `@MockBean` can stand in.

---

### TICKET-ADV076 — MockMvc: unauthenticated returns 401

**Goal:** Prove that a POST with no authentication is rejected before reaching the controller.

**What**
- `testCreateTrade_unauthenticated_returns401` method on the same `TradeControllerWebMvcTest` class, with no `@WithMockUser` and no `.with(csrf())`, asserting `status().isUnauthorized()`.

**Why**
- This is the negative-path twin of ADV075 — together they pin down the 201/401 boundary so an accidental `permitAll()` widening in `SecurityConfig` fails the build immediately.

**Observe**
- Test is green with assertion `status().isUnauthorized()` (401); a 403 here instead of 401 means anonymous CSRF rejection fired ahead of the auth check — confirm `STATELESS` and CSRF disabled in `SecurityConfig`.

**Done when:**
- The test method has no `@WithMockUser` annotation.
- The assertion is `status().isUnauthorized()` (401), not `isForbidden()` (403).
- No service interaction is verified — the request never reaches the controller.

<details>
<summary>Hint 1 — gentle direction</summary>

What is the difference between "I do not know who you are" and "I know
who you are but you cannot do this"? Both are failures, both have
distinct status codes. If a caller does not authenticate at all,
which one fires?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Reuse the same `@WebMvcTest` setup as TICKET-ADV075 but drop the
`@WithMockUser` annotation from this test method. Keep the
`SecurityMockMvcRequestPostProcessors.csrf()` off too — when there is
no authenticated user, the filter chain bounces the request before
CSRF is evaluated.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Build the same POST as TICKET-ADV075 with the JSON body, omit
`@WithMockUser`, omit the CSRF postprocessor, then assert
`status().isUnauthorized()`. No need to stub the service — the
request will never reach it.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a second test method inside the existing `@WebMvcTest(TradeController.class)` class — no new class needed.
2. Do NOT annotate it with `@WithMockUser` — leave the request anonymous.
3. Build the same POST body as TICKET-ADV075, omit `.with(csrf())` postprocessor.
4. Assert `status().isUnauthorized()` (401), not `isForbidden()` (403).
5. Skip any `verify(tradeService, ...)` calls — the controller never executes.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/controller/TradeControllerWebMvcTest.java` — additional method):

```java
@Test
void testCreateTrade_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isUnauthorized());
}
```

</details>

**▶ Run the project — verify TICKET-ADV076 end-to-end**

Run the unauthenticated MockMvc test method only.

```bash
./mvnw -pl backend test -Dtest=TradeControllerWebMvcTest#testCreateTrade_unauthenticated_returns401
```

**Observe:**

- Test passes green; assertion is `status().isUnauthorized()` (401).
- The service is never called — Mockito's default verification shows zero interactions with `tradeService`.
- A 403 here instead of 401 means anonymous CSRF rejection beat the auth check; ensure `STATELESS` + CSRF disabled in `SecurityConfig`.

---

### TICKET-ADV077 — MockMvc: VIEWER returns 403

**Goal:** Prove that an authenticated user with the wrong role gets 403 on a state-changing endpoint.

**What**
- `testCreateTrade_viewerRole_returns403` method on `TradeControllerWebMvcTest` annotated `@WithMockUser(roles = "VIEWER")`, posting the same `validRequest()` with `.with(csrf())`, and asserting `status().isForbidden()` without stubbing the service.

**Why**
- This is the regression net for the RBAC matrix wired in ADV074 — if a future commit accidentally adds `VIEWER` to the trade-create allow-list, this test flips red before the change ships.

**Observe**
- `./mvnw test -Dtest=TradeControllerWebMvcTest` shows all three role-coverage methods green (201, 401, 403); a 201 for the VIEWER test is the exact regression the matrix is meant to catch.

**Done when:**
- The test runs with `@WithMockUser(roles = "VIEWER")`.
- The assertion is `status().isForbidden()` (403).
- The service stub is not called — denial happens at the security layer, not the controller.

<details>
<summary>Hint 1 — gentle direction</summary>

VIEWER is read-only. Your `SecurityFilterChain` from TICKET-ADV074 should
have a rule that POST to `/api/v1/trades` requires TRADER or ADMIN.
Where in the request lifecycle is that rule evaluated, and what
response code does Spring Security produce when an authenticated user
fails it?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Reuse the TICKET-ADV075 setup. Switch `@WithMockUser(roles = "TRADER")` to
`@WithMockUser(roles = "VIEWER")`. Keep the CSRF postprocessor — the
user is authenticated, just not authorised. The assertion changes to
`isForbidden()`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Same JSON body, same CSRF, role set to `"VIEWER"`, expect status 403.
No service stub needed — the call never gets through. This is the
test that catches a regression where someone accidentally widens the
role list in `SecurityConfig`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Add a third test method to the existing `@WebMvcTest(TradeController.class)` class.
2. Annotate it `@WithMockUser(roles = "VIEWER")` — the user is authenticated but lacks the TRADER/ADMIN role required for POST.
3. Reuse the `validRequest()` helper and add `.with(csrf())` (CSRF still needs the postprocessor because the user is authenticated).
4. Assert `status().isForbidden()` (403).
5. Skip any service stubbing — the controller method is never reached.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/controller/TradeControllerWebMvcTest.java` — additional method):

```java
@Test
@WithMockUser(roles = "VIEWER")
void testCreateTrade_viewerRole_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/trades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest()))
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isForbidden());
}
```

</details>

**▶ Run the project — verify TICKET-ADV077 end-to-end**

Run the full WebMvc test class — all three role-coverage methods should be green.

```bash
./mvnw -pl backend test -Dtest=TradeControllerWebMvcTest
```

**Observe:**

- All three methods pass: `..._authenticated_returns201`, `..._unauthenticated_returns401`, `..._viewerRole_returns403`.
- Each test asserts a distinct status code — 201, 401, 403 — covering the happy path plus two security boundaries.
- A 201 instead of 403 for the VIEWER test means someone added `VIEWER` to the POST role list in `SecurityConfig` — this test catches that regression.

---

### TICKET-ADV078 — Full lifecycle integration test with Testcontainers

**Goal:** Drive a real Postgres-backed Spring Boot through the full trade lifecycle over HTTP.

**What**
- `TradeLifecycleIT` annotated `@SpringBootTest(webEnvironment = RANDOM_PORT) @Testcontainers` with a static `PostgreSQLContainer<>("postgres:16-alpine")` marked `@ServiceConnection`, using `TestRestTemplate` + ordered `@TestMethodOrder` tests that log in as `admin@db.com`, capture the JWT, then drive POST/GET/PATCH/recon-run/resolve over real HTTP.

**Why**
- This is the load-bearing end-to-end test for Workshop 5 — it proves Liquibase + JWT + RBAC + every controller from ADV063-074 wire together on a real Postgres, the same way Day 10's CI runs the suite.

**Observe**
- `./mvnw test -Dtest=TradeLifecycleIT` is green; Testcontainers logs show a `postgres:16-alpine` container starting and stopping; a connection-refused error means Docker is not running locally.

**Done when:**
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` boots the app on a random port wired to a `PostgreSQLContainer<>("postgres:16-alpine")` via `@ServiceConnection`.
- Ordered tests log in as `admin@db.com`, capture the JWT, then exercise POST /trades, GET /trades, PATCH /status, POST /recon/run, PUT /recon/results/{id}/resolve — every step's status code is asserted.
- The test class runs green on a fresh machine with Docker available.

<details>
<summary>Hint 1 — gentle direction</summary>

You want one Postgres container shared by every test in the class
(starting one per method would take minutes). You also want Spring's
`DataSource` to be wired to that container automatically — no manual
`@DynamicPropertySource`. Look at Testcontainers' Spring Boot 3.1+
integration.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `@Testcontainers` on the class and declare a static
`PostgreSQLContainer<?>` field marked `@Container` and
`@ServiceConnection`. The `@ServiceConnection` annotation is from
`org.springframework.boot.testcontainers.service.connection`. Force
test order with `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`
and `@Order(1..6)` on each method. Hold the captured token and ids in
static fields so later steps can use them.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`@Test @Order(1) loginAsAdmin()` posts credentials to
`/api/auth/login`, asserts 200, stashes `token` from the JSON.
Subsequent steps build an `HttpHeaders` with
`setBearerAuth(token)` and call the API through `RestTemplate`. Each
step asserts its expected status: 201 for create, 200 for list with
`totalElements` at least 1, 200 for patch with status becoming
`MATCHED`, 202 for recon trigger, 200 for resolve with status
becoming `RESOLVED`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `TradeLifecycleIT` in `src/test/java/.../integration/` annotated `@Testcontainers @SpringBootTest(webEnvironment = RANDOM_PORT) @ActiveProfiles("test") @TestMethodOrder(MethodOrderer.OrderAnnotation.class)`.
2. Declare a static `PostgreSQLContainer<>("postgres:16-alpine")` field marked `@Container @ServiceConnection` so Spring auto-wires the DataSource.
3. Use static fields for `token`, `createdId`, `reconJobId`, `breakId` so they persist across ordered tests.
4. `@Order(1) loginAsAdmin()` — POST `/api/auth/login`, assert 200, stash `token` from the JSON.
5. `@Order(2-6)` — exercise POST /trades (201), GET /trades (200 with totalElements ≥ 1), PATCH /status (200, MATCHED), POST /recon/run (202, jobId), PUT /recon/results/{id}/resolve (200, RESOLVED).
6. Each step builds `HttpHeaders` with `setBearerAuth(token)` and calls through `RestTemplate`.
7. Run with `./mvnw test` against a Docker daemon; confirm green.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/integration/TradeLifecycleIT.java`):

```java
package com.dbtraining.reconx.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradeLifecycleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort int port;
    @Autowired ObjectMapper om;

    static String token;
    static Long createdId;
    static String reconJobId;
    static Long breakId;

    RestTemplate http = new RestTemplate();

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    @Test @Order(1)
    void loginAsAdmin() {
        var body = """
                {"username":"admin@db.com","password":"admin123"}
                """;
        var req = new HttpEntity<>(body, new HttpHeaders() {{
            setContentType(MediaType.APPLICATION_JSON);
        }});
        var resp = http.postForEntity(
                "http://localhost:" + port + "/api/auth/login", req, JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        token = resp.getBody().get("token").asText();
        Assertions.assertNotNull(token);
    }

    @Test @Order(2)
    void createTrade() {
        // tradeRef regex: ^[A-Z]{3}-\d{8}-\d{4}$. assetClass and side are @NotBlank on
        // TradeRequest; status is server-side and must NOT appear in the request body.
        var body = """
                {"tradeRef":"INT-20260315-0001","instrumentId":1,"counterpartyId":1,
                 "assetClass":"EQUITY","side":"BUY",
                 "quantity":100.0,"price":245.50,"tradeDate":"2026-03-15"}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        createdId = resp.getBody().get("id").asLong();
    }

    @Test @Order(3)
    void getTradeBack() {
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades?status=PENDING",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertTrue(resp.getBody().get("totalElements").asLong() >= 1);
    }

    @Test @Order(4)
    void patchStatus() {
        var body = """
                {"status":"MATCHED"}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades/" + createdId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertEquals("MATCHED", resp.getBody().get("status").asText());
    }

    @Test @Order(5)
    void triggerRecon() {
        var body = """
                {"from":"2026-03-01","to":"2026-03-31"}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/recon/run",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        reconJobId = resp.getBody().get("jobId").asText();
    }

    @Test @Order(6)
    void resolveBreak() {
        // (Test data seeded by Liquibase guarantees at least one break with id 1.)
        breakId = 1L;
        var body = """
                {"note":"Confirmed via counterparty email on 2026-03-16."}
                """;
        var resp = http.exchange(
                "http://localhost:" + port + "/api/v1/recon/results/" + breakId + "/resolve",
                HttpMethod.PUT, new HttpEntity<>(body, authHeaders()), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        Assertions.assertEquals("RESOLVED", resp.getBody().get("status").asText());
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV078 end-to-end**

Run the Testcontainers-backed integration test (Docker must be running).

```bash
./mvnw -pl backend verify -Dit.test=TradeLifecycleIT
```

**Observe:**

- A `postgres:16-alpine` container starts (visible in `docker ps` mid-run) and is reaped at the end.
- All 6 ordered test methods pass — `loginAsAdmin`, `createTrade`, `getTradeBack`, `patchStatus`, `triggerRecon`, `resolveBreak`.
- Final test asserts `status == RESOLVED`; an `OptimisticLockingFailureException` mid-run usually means missing `@Order` annotations and a race between methods.

---

### TICKET-ADV079 — Verify Liquibase ran on a fresh DB

**Goal:** Prove that every Liquibase changeset applies cleanly against an empty Postgres and that seed data lands.

**What**
- `LiquibaseMigrationsIT` annotated `@Testcontainers @SpringBootTest @ActiveProfiles("test")` with its own `PostgreSQLContainer` and `@Autowired JdbcTemplate`, asserting `SELECT COUNT(*) FROM databasechangelog >= 13` and `SELECT COUNT(*) FROM trades WHERE deleted_at IS NULL >= 10`.

**Why**
- This is the safety net for the Day 1 / Day 4 / Day 5 changelog stack — if a developer forgets to commit a new changeset XML, this test fails on a fresh container before the regression reaches the Day 10 pipeline.

**Observe**
- `./mvnw test -Dtest=LiquibaseMigrationsIT` is green; failure with `relation "databasechangelog" does not exist` means Liquibase never ran — check `spring.liquibase.enabled` and the changelog path.

**Done when:**
- A second integration test class spins up its own `PostgreSQLContainer` and asserts the row count of `databasechangelog` is at least the expected number.
- It asserts at least 10 rows in `trades` with `deleted_at IS NULL` (the seed data).
- The test catches the case where a developer forgot to commit a new changeset XML — running on a clean container fails immediately.

<details>
<summary>Hint 1 — gentle direction</summary>

What table does Liquibase use to record which changesets have been
applied? If you boot against a fresh Postgres, how many rows should
that table have at the end? Plus the seed data — how many trades
should the seed insert?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `@SpringBootTest` plus `@Testcontainers` and a
`@ServiceConnection` container, the same pattern as TICKET-ADV078. Inject
`JdbcTemplate` and run `SELECT COUNT(*) FROM databasechangelog` and
`SELECT COUNT(*) FROM trades WHERE deleted_at IS NULL`. Use AssertJ
for fluent assertions.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Count expected changesets: Day 1 (init + schema + seed + views +
audit), Day 4 (Envers tables), Day 5 (`deleted_at` column). Assert the
applied total is `greaterThanOrEqualTo` that number — be tolerant of
future additions. Seed: at least 10 non-deleted trades.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `LiquibaseMigrationsIT` annotated `@Testcontainers @SpringBootTest @ActiveProfiles("test")` in `src/test/java/.../integration/`.
2. Declare a static `PostgreSQLContainer<>("postgres:16-alpine")` marked `@Container @ServiceConnection` — Spring wires the DataSource automatically.
3. `@Autowire JdbcTemplate`.
4. Inside the test method, count rows from `databasechangelog` with `SELECT COUNT(*)` and assert it is `greaterThanOrEqualTo` the expected number (Day 1 = 9, Day 4 = +3, Day 5 = +1 → 13).
5. Count non-deleted seed trades with `SELECT COUNT(*) FROM trades WHERE deleted_at IS NULL` and assert it is `greaterThanOrEqualTo(10)`.
6. Run with `./mvnw test`; failure on a fresh container signals a missing changeset commit.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/integration/LiquibaseMigrationsIT.java`):

```java
package com.dbtraining.reconx.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class LiquibaseMigrationsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;

    @Test
    void liquibase_applied_all_expected_changesets() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog", Integer.class);
        // Day 1 = 9 changesets (init + schema + seed + views + audit)
        // Day 4 = +3 Envers tables. Day 5 = +1 deleted_at column. Total expected = 13.
        assertThat(applied).isGreaterThanOrEqualTo(13);

        Integer trades = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trades WHERE deleted_at IS NULL", Integer.class);
        assertThat(trades).isGreaterThanOrEqualTo(10);   // seed_data.sql
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV079 end-to-end**

Run the Liquibase migrations check against a fresh container.

```bash
./mvnw -pl backend test -Dtest=LiquibaseMigrationsIT
```

**Observe:**

- A fresh `postgres:16-alpine` container starts and Liquibase applies every changeset from an empty schema.
- `databasechangelog` row count is at least 13 (Day 1 = 9, Day 4 = +3, Day 5 = +1) and `trades` has at least 10 non-deleted seed rows.
- Failure with `relation "databasechangelog" does not exist` means Liquibase never ran — check `spring.liquibase.enabled` and the changelog path.

---

### TICKET-ADV080 — API versioning + deprecation

**Goal:** Establish `/api/v1/...` as the contract prefix and demonstrate how to deprecate an endpoint cleanly.

**What**
- Every controller class carrying `@RequestMapping("/v1/...")` (combined with `server.servlet.context-path=/api` to yield `/api/v1/...`), plus one `@Deprecated(since = "v1.4.0", forRemoval = true)` example endpoint that sets `Deprecation: true`, `Sunset: <HTTP-date>`, and `Link: </api/v1/...>; rel="successor-version"` headers and returns `ResponseEntity.status(HttpStatus.GONE)`.

**Why**
- Pinning the version prefix now is what lets Day 8 React point its Vite proxy at a stable `/api/v1` and what gives Day 10's deployment a clean path to ship `/api/v2` without breaking in-flight clients.

**Observe**
- `curl -i /api/v0/trades` returns `HTTP/1.1 410 Gone` with the three deprecation headers; `curl -i /api/v1/trades` returns 200; a 404 instead of 410 on the deprecated path means the handler is not registered and callers cannot tell the surface area was deliberately retired.

**Done when:**
- Every controller's `@RequestMapping` carries the `/api/v1/` prefix.
- An example deprecated endpoint returns HTTP 410 Gone with `Deprecation: true`, a `Sunset` date header, and a `Link` header pointing at the successor.
- A short note in the README explains the rule: breaking changes get a new version segment; the old segment keeps working until its sunset date.

<details>
<summary>Hint 1 — gentle direction</summary>

Why does the version go on every endpoint and not just at the router?
Imagine shipping `/api/v2/trades` with a breaking change to the
response shape. What has to remain true for clients still on the old
contract? Where would they keep calling, and what would the server
have to keep serving?

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

There are three standard HTTP headers for deprecation. `Deprecation:
true` signals the endpoint is deprecated. `Sunset: <HTTP-date>` tells
callers when it will be removed. `Link: <new-url>; rel="successor-version"`
points to the replacement. Java's `@Deprecated(since = "...",
forRemoval = true)` is the right source-level annotation.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

On every controller class: `@RequestMapping("/api/v1/...")`. For the
deprecation example, annotate one old method with `@Deprecated(since
= "v1.4.0", forRemoval = true)`, in its body set the three headers on
the `HttpServletResponse`, and return a response of status
`HttpStatus.GONE`. Document the versioning rule in the project README
so it survives team turnover.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Audit every controller — confirm each `@RequestMapping` carries the `/v1/...` prefix (combined with the `/api` context-path, the full URL is `/api/v1/...`).
2. Pick one old endpoint to deprecate (or add an example surface area like `/old-search`).
3. Annotate the method with `@Deprecated(since = "v1.4.0", forRemoval = true)` so Java tooling flags callers.
4. Inside the method, set three response headers: `Deprecation: true`, `Sunset: <HTTP-date>`, `Link: </api/v1/...>; rel="successor-version"`.
5. Return `ResponseEntity.status(HttpStatus.GONE).build()` (410).
6. Add a short note to the project README explaining: breaking changes ship under `/api/v2/`; `/api/v1/` keeps working until its `Sunset` date.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/controller/TradeController.java` — class + deprecated example):

```java
// Every controller carries the versioned prefix:
@RestController
@RequestMapping("/v1/trades")
public class TradeController { /* ... */ }

// Example deprecation of an old endpoint surface area:
@Deprecated(since = "v1.4.0", forRemoval = true)
@GetMapping(value = "/old-search", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<Void> oldSearch(HttpServletResponse response) {
    response.setHeader("Deprecation", "true");
    response.setHeader("Sunset", "Sat, 1 Jul 2026 00:00:00 GMT");
    response.setHeader("Link",
            "</api/v1/trades?status=...>; rel=\"successor-version\"");
    return ResponseEntity.status(HttpStatus.GONE).build();
}
```

</details>

**▶ Run the project — verify TICKET-ADV080 end-to-end**

Hit the deprecated v0 endpoint and the current v1 endpoint and compare responses.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@db.com","password":"admin123"}' | jq -r '.token')

# Deprecated path → 410 Gone with deprecation headers
curl -i http://localhost:8080/api/v0/trades \
  -H "Authorization: Bearer $TOKEN"

# Current path → 200
curl -i http://localhost:8080/api/v1/trades \
  -H "Authorization: Bearer $TOKEN"
```

**Observe:**

- v0 response is `HTTP/1.1 410 Gone` with `Deprecation: true`, `Sunset: <HTTP-date>`, and `Link: </api/v1/...>; rel="successor-version"` headers set.
- v1 response is `HTTP/1.1 200 OK` with the normal paginated trades body.
- A 404 on `/api/v0/trades` instead of 410 means the deprecated handler is not registered — clients then think the surface area never existed, which is worse than a clear deprecation signal.

---

## End-of-day checklist

- [ ] All nine controller endpoints (TICKET-ADV063 – TICKET-ADV071) live, returning the correct status codes (200, 201, 202, 204, 400, 401, 403, 404), and visible in Swagger UI.
- [ ] `POST /api/auth/login` returns a valid JWT and jwt.io decodes it with the configured secret.
- [ ] `JwtAuthenticationFilter` is registered before `UsernamePasswordAuthenticationFilter` in the chain.
- [ ] RBAC matrix enforced — VIEWER cannot POST, only ADMIN can DELETE, RECON_ANALYST can resolve breaks — verified by MockMvc tests for 401 and 403.
- [ ] `TradeLifecycleIT` runs against a Testcontainer and exercises login, create, list, patch, recon trigger, resolve.
- [ ] `LiquibaseMigrationsIT` passes on a clean container and checks both changelog and seed counts.
- [ ] Every controller is prefixed `/api/v1/` and a deprecation example is documented.
- [ ] No JWT secret is committed to git — all references go through `${APP_JWT_SECRET:dev-default}`.
- [ ] Soft-deleted trades disappear from list endpoints but remain visible in the database for audit.
- [ ] You can draw the request flow from `curl POST /api/v1/trades` with a Bearer token all the way to the database, naming every Spring component the request passes through.
