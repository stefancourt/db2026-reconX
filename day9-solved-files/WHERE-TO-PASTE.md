# Day 9 — Solved Files & How To Run

Day 9 is the Kafka day. You wire the event backbone: a producer that
publishes every trade-state change onto `trade-events`, three consumers
that fan out to reconciliation / audit / alerts, an error handler with
retry + DLQ, and a topics-config bean that declares everything on
startup so `docker compose up` is one command.

**How this folder works**

The real `backend/` tree ships all six Kafka files as starter stubs —
method bodies do `throw new UnsupportedOperationException("…")` with a
`TODO(TICKET-…)` comment above each. This folder contains **complete
drop-in replacement files** for all six:

- ✅ `KafkaTopicsConfig.java` — declares all four topics via `TopicBuilder`.
- ✅ `TradeEventProducer.java` — publishes via `KafkaTemplate.send(topic, tradeRef, event)` with success/failure logging.
- ✅ `ReconciliationConsumer.java` — `@KafkaListener` on `trade-events` (group `recon-service`).
- ✅ `AuditEventConsumer.java` — `@KafkaListener` on `trade-events` (group `audit-service`), persists to `AuditLogEntry`.
- ✅ `AlertConsumer.java` — `@KafkaListener` on `system-alerts` (group `alert-service`).
- ✅ `KafkaErrorHandlerConfig.java` — `DefaultErrorHandler` with `ExponentialBackOff(1000, 2.0)` capped at 3 attempts + `DeadLetterPublishingRecoverer` routing to `{topic}-dlq`.

You can **overlay** the whole `backend/` subtree in one shot, or
**open each file** in this folder side-by-side with the starter to
read the diff first.

## Quick start

```bash
# From the project root — one-shot overlay:
cp -R day9-solved-files/backend/ backend/
```

---

## Ticket status

Day 9 has 18 tickets (ADV128–145). The heavy config lives in
`application.yml` (already in the starter) and in `docker-compose.yml`
(project root, also in the starter).

| Ticket | Status | Where |
|---|---|---|
| ADV128 — Declare topics on startup | ⏳ TODO in your code | `KafkaTopicsConfig.java` |
| ADV129 — `TradeEventProducer.publish(TradeEvent)` | ⏳ TODO in your code | `TradeEventProducer.java` |
| ADV130 — TradeEvent DTO shape | ✓ already in starter | `dto/TradeEvent.java` |
| ADV131 — `ReconciliationConsumer` (@KafkaListener on trade-events) | ⏳ TODO in your code | `ReconciliationConsumer.java` |
| ADV132 — `AuditEventConsumer` writes to audit_log | ⏳ TODO in your code | `AuditEventConsumer.java` |
| ADV133 — `AlertConsumer` on system-alerts | ⏳ TODO in your code | `AlertConsumer.java` |
| ADV134 — Error handler with retry + DLQ | ⏳ TODO in your code | `KafkaErrorHandlerConfig.java` |
| ADV135 — Consumer group config / offset reset | ✓ in `application.yml` | `spring.kafka.consumer.*` |
| ADV136 — Idempotent producer + ack config | ✓ in `application.yml` | `spring.kafka.producer.*` |
| ADV137 — Trusted packages for JSON deser | ✓ in `application.yml` | `spring.json.trusted.packages` |
| ADV138 — `GET /v1/audit/trades/{ref}/events` | ⏳ Day-5 controller TODO | `AuditController.events` |
| ADV139–142 — Kafka Streams / KTable / windowing | optional stretch | ships as follow-on ticket set |
| ADV143 — SSE bridge `/api/v1/trades/stream` | ✓ already in starter | `controller/TradeController` (or dedicated SSE controller) |
| ADV144 — Kafdrop for topic browsing | ✓ in `docker-compose.yml` | port 9000 |
| ADV145 — Consumer-lag metric | ✓ Micrometer auto-registers `kafka_consumer_lag` when Kafka client is on classpath | — |

---

## How to finish the six files

Each of the six Kafka files in your project already has its
implementation shape spelled out in the TODO block at the top of the
file. You don't need to open the student guide to do them — the hint
pseudocode in your own code is enough. Here's the map:

| File | What it needs |
|---|---|
| `KafkaTopicsConfig.java` | Four `@Bean NewTopic` methods using `TopicBuilder.name(...).partitions(N).replicas(1).build()`. Topic names: `trade-events`, `recon-results`, `system-alerts`, `trade-events-dlq`. |
| `TradeEventProducer.java` | Inject `KafkaTemplate<String, TradeEvent>`, then `publish(event)` calls `template.send("trade-events", event.tradeRef(), event)`. Optional: `.whenComplete((res, err) -> ...)` for logging. |
| `ReconciliationConsumer.java` | `@KafkaListener(topics = "trade-events", groupId = "reconx-recon")` on a method that takes `TradeEvent` and passes it to `ReconciliationEngine`. |
| `AuditEventConsumer.java` | `@KafkaListener(topics = "trade-events", groupId = "reconx-audit")` on a method that maps `TradeEvent` → `AuditLogEntry` and calls `auditRepo.save(...)`. |
| `AlertConsumer.java` | `@KafkaListener(topics = "system-alerts", groupId = "reconx-alerts")` — log, optionally publish a metric. |
| `KafkaErrorHandlerConfig.java` | `@Bean DefaultErrorHandler` with a `DeadLetterPublishingRecoverer(kafkaTemplate, (rec, ex) -> new TopicPartition("trade-events-dlq", rec.partition()))` and a `FixedBackOff` for retries. |

Compile after each file; the app boots even with `@KafkaListener` bodies
throwing, so you can iterate one at a time.

---

## Run the project

You need Docker for this day — Kafka is not embeddable easily.

### Before you start

1. **Java 21** on the terminal that runs the backend: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
2. **Days 1–6 are applied** (Day 9 depends on the earlier layers, especially the recon engine and audit log):
   ```bash
   for d in day1 day2 day3 day4 day5 day6; do cp -R ${d}-solved-files/backend/ backend/; done
   ```
3. **Docker Desktop / colima** running: `docker ps` should not error.

### Terminal 1 — Kafka + Kafdrop via docker compose

```bash
cd /Users/siddharthsharma/Downloads/OneDrive_1_22-07-2026
docker compose up -d kafka kafdrop
# wait a few seconds
docker compose logs kafka | tail   # should end with "started (kafka.server.KafkaServer)"
```

Kafdrop UI: <http://localhost:9000> (once you finish
`KafkaTopicsConfig`, the four topics show up here after the first
backend boot).

### Terminal 2 — backend

```bash
cd backend
# Use the "uat" profile so KafkaTopicsConfig is picked up (it's @Profile("!dev & !test"))
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run
```

Wait for `Started ReconxApplication`. On the dev profile Kafka listener
failures are non-fatal (`missing-topics-fatal: false`), so the app will
boot even if Kafka isn't up — but no messages will flow.

### Prove the pipeline

Once you've filled in the producer + a consumer:

```bash
# 1. Log in (Day 5) and POST a trade (Day 5)
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@reconx.local","password":"password"}' | jq -r .token)

curl -X POST http://localhost:8081/api/v1/trades \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"EQU-20260603-0001","instrumentId":1,"counterpartyId":1,
       "assetClass":"EQUITY","side":"BUY","quantity":100,"price":100,"tradeDate":"2026-06-03"}'

# 2. Open Kafdrop and browse "trade-events" — the message should be there
open http://localhost:9000

# 3. Check the audit log picked it up
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/api/v1/audit/trades/EQU-20260603-0001/events | jq
```

Hit `Ctrl+C` when done. `docker compose down` cleans up Kafka.

---

## What success looks like

- `docker compose up -d kafka kafdrop` reports "Started".
- Kafdrop at <http://localhost:9000> lists `trade-events`, `recon-results`, `system-alerts`, `trade-events-dlq` after the first backend boot.
- POSTing a trade puts a message on `trade-events` (browse via Kafdrop).
- The audit-log consumer writes a row into `audit_log` — `GET /v1/audit/trades/{ref}/events` returns it.
- Deliberately throw inside a consumer, POST another trade → the message ends up in `trade-events-dlq` after N retries.
- `/actuator/prometheus | grep kafka_consumer_lag` reports current lag per consumer group.

---

## Troubleshooting

- **`Broker may not be available`** — Kafka container isn't up or the port isn't exposed. `docker compose logs kafka | tail -20` and confirm port 9092 mapping in `docker-compose.yml`.
- **Consumer reads nothing even though producer sent** — `spring.kafka.consumer.auto-offset-reset=earliest` is set in `application.yml`; if you overrode it to `latest`, older messages won't be replayed. Also confirm the consumer's `groupId` is distinct — multiple consumers sharing a group split the partitions.
- **`ClassCastException` deserializing `TradeEvent`** — `spring.json.trusted.packages` doesn't include `com.dbtraining.reconx.dto`. Fix in `application.yml`.
- **DLQ topic doesn't exist** — `KafkaTopicsConfig` isn't picking it up in your profile. Either boot with `SPRING_PROFILES_ACTIVE=uat` or pre-create the topic via `kafka-topics --create`.
- **Idempotent producer errors on retry** — the producer config in `application.yml` sets `enable.idempotence: true`; combined with `acks=all` the broker takes care of dedup.

Kafka is fiddly; being able to see messages in Kafdrop is 90 % of the debugging.
