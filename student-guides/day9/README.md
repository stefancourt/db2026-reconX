# Day 9 — Student Guide

> **Trainer-facing equivalent:** [TrainersGuide/day9/README.md](../../TrainersGuide/day9/README.md)
> **Module:** React Testing + Apache Kafka Deep Dive

> ## NOTE — Read this before you start
>
> **1. Start Kafka before standup.** The afternoon tickets all need a running broker. From the project root, run:
> ```bash
> docker compose up -d kafka zookeeper kafdrop
> ```
> Wait until `docker compose ps` shows all three as `healthy` (usually ~30 seconds). If `docker compose` complains the services aren't defined yet, peek ahead at **Day 10's Compose block** — that's where the full 7-service stack is introduced, but the `kafka`/`zookeeper`/`kafdrop` services are already wired and safe to start today. Open Kafdrop at <http://localhost:9000> in a tab and keep it pinned all afternoon.
>
> **2. Package paths in Hint 4 are `kafka/`, not `config/`.** Earlier drafts of this guide placed `KafkaTopicsConfig`, `KafkaErrorHandlerConfig`, etc. under `com.dbtraining.reconx.config`. That was wrong — the trainer copy keeps all Kafka wiring in `com.dbtraining.reconx.kafka`. The Hint 4 "Reference solution" paths now reflect the correct location. A few **Steps** sections may still say `config/` — trust the Reference-solution path over the Step text. If in doubt, `grep -r "TICKET-ADV13" reconx-trainercopy/` from the project root will land you in the right file.
>
> **3. Read the "Why we're doing Kafka today" section below before opening Hint 1 on any ticket.** The exercises make a lot more sense once you understand why we picked the patterns we did.

## What you'll build today

The morning is theory-led. You will sit through an RTL + Jest mini-lecture covering `render`, `screen`, `userEvent`, query priority, async `findBy*`, fetch/MSW mocking, and the difference between snapshot tests and behavioural tests. There are no graded React exercises in this guide — those land on Day 10 alongside the demo polish. Take notes; the patterns reappear when you stabilise the trade dashboard tests tomorrow.

The afternoon is the heavy lift. You will build an event-driven backbone on Apache Kafka across 18 graded exercises (TICKET-ADV128 – TICKET-ADV145). By end-of-day you will have three working topics, a keyed producer, three independent consumer groups (recon, audit, alert), a dead-letter queue with exponential backoff retry, a replay endpoint, an event-sourced audit rebuild, Grafana panels for lag and DLQ count, Testcontainers integration tests against a real broker, and a Claude-reviewed consumer configuration. The single goal: a Kafka pipeline that would survive a production incident review.

Out of scope today: Kafka Streams, KSQL, Schema Registry with Avro, and Confluent Cloud. If you read the delivery-plan doc and wondered about those — they are deliberately deferred. Use JSON payloads and the standard `StringSerializer`/`JsonSerializer` everywhere.

## Why we're doing Kafka today

Before you start clicking through Hint 1 on the first ticket, internalise *why* the recon platform is being rewritten around an event bus instead of staying with the synchronous REST calls you built on Day 5. Every design choice today flows from one of these motivations:

- **Decoupling producers from consumers.** Up to Day 8, when a trader posts a trade via `POST /api/v1/trades`, the controller has to *also* trigger reconciliation, *also* write the audit row, *also* update the dashboard. Add a fourth side-effect (compliance feed, alert email) and the controller becomes a god-object. With Kafka, the controller just publishes one `TradeEvent` and walks away. Recon, audit, alerts, and the dashboard each subscribe independently. New consumers can be added without touching `TradeController`. That is what "event-driven architecture" actually buys you.

- **Independent SLAs and back-pressure.** Reconciliation is CPU-heavy and can lag during EOD batches. The audit log is cheap but legally mandated to never drop a write. Alerts need sub-second latency. Three different SLAs, three different scaling profiles. Kafka gives each consumer its own consumer group, its own lag, its own scaling story. A slow recon worker can't block the audit write. That is why **TICKET-ADV131 / ADV132 / ADV133** are three separate consumers with three different groups — not one consumer doing a `switch` on event type.

- **Resilience without losing data.** REST calls fail by throwing — your `try/catch` either swallows the error (silent data loss) or bubbles it (failed user request). Kafka fails by stalling the partition — the message stays on disk until you fix the bug or move it aside. That is exactly what the DLQ pattern in **TICKET-ADV134 / ADV135 / ADV136** is for: retry a few times with backoff, then quarantine the bad message so the good ones keep flowing. In production this is the difference between "one trade broke recon for everyone for 30 minutes" and "one trade landed in DLQ for ops to investigate, the rest reconciled normally".

- **Audit trail you can rebuild.** Every state change in the trade lifecycle is published as an immutable event. If the `trades` table is ever corrupted, dropped, or mis-migrated, you can replay the event log from offset 0 and reconstruct every trade's full history. That is the point of **TICKET-ADV137 (event sourcing rebuild)** — and the reason auditors take event-sourced systems seriously. The Day 4 `@Audited` Envers table is "remember what changed"; Kafka is "the events *are* the source of truth, the table is just a cached projection".

- **Observable in production.** Kafka exposes consumer lag, throughput, and DLQ depth as first-class metrics. You wire those into the Day 6 Prometheus + Grafana stack in **TICKET-ADV139 / ADV140 / ADV141 / ADV142**. Once lag is on a dashboard and DLQ count fires an alert, oncall can tell at a glance whether the platform is healthy without reading logs. That is the **"would survive an incident review"** goal from the day intro.

- **Tested against the real thing.** A mocked Kafka client tests your code, not your *configuration*. Topic name typos, wrong partition counts, missing `groupId` — all invisible to a mock. **TICKET-ADV143 / ADV144** run your code against a real broker in Testcontainers, which catches the bugs that only appear when bytes actually cross a network. That's why the trainer copy ships Testcontainers + a `confluentinc/cp-kafka:7.6.0` image instead of letting you use `EmbeddedKafka`.

Keep this list open in a tab while you work the tickets. Whenever a Hint 3 says "now do X" and you find yourself wondering *why* — the answer is always one of the six bullets above.

## Day at a glance

1. Standup and Day-8 holdover unblock. Make sure your React forms and table from Day 8 are green before the AM lecture starts.
2. React Testing mini-lecture. RTL + Jest fundamentals. No exercises; notes only.
3. Workshop 9 Part A: topics, producer, payload. Exercises TICKET-ADV128 – TICKET-ADV130.
4. Lunch.
5. Apache Kafka Deep Dive lecture (2 hours). Brokers, partitions, consumer groups, error handling, DLQ, retry patterns, event sourcing, Kafka + Prometheus metrics. Whiteboard-heavy.
6. Workshop 9 Part B: consumers. Exercises TICKET-ADV131 – TICKET-ADV133.
7. Workshop 9 Part C: DLQ, retry, replay, event sourcing. Exercises TICKET-ADV134 – TICKET-ADV138.
8. Workshop 9 Part D: metrics, Grafana, integration tests, AI review. Exercises TICKET-ADV139 – TICKET-ADV145.
9. End-of-day debrief and Day-10 preview.

## Exercises

There are 18 Kafka-focused exercises spread across four workshop blocks. Hints below are deliberately progressive: Hint 1 is a gentle conceptual nudge, Hint 2 names the file or API you need to look at, Hint 3 sketches the shape of the answer without writing code. If Hint 3 isn't enough, pair with another student or wave a trainer over — do not skip ahead and copy from a neighbour's screen. The point of the ladder is the climb.

A few cross-cutting rules to internalise before you start:

- The local Kafka broker is single-node. Replicas must be `1` everywhere, never `3`. Production would be `3` with `min.insync.replicas=2`; we are not in production.
- Topics should be declared as Spring beans, not created by `kafka-topics.sh`. CI and the demo laptop both need the topics to exist on cold start.
- Every consumer is in its own consumer group. Same group = load balancing; different groups = fan-out. Today we want fan-out for the recon, audit, and alert consumers.
- Never put a JPA entity on the wire. Map to a DTO or `JsonNode` snapshot before publishing or you will hit `LazyInitializationException` inside the producer's `send()`.
- Use `Instant` (UTC) for event timestamps. `LocalDateTime` makes the consumer guess the timezone.
- Use Kafdrop at `http://localhost:9000` constantly. Refresh it after every produce, every consumer start, every DLQ event. It is the closest thing you have to a debugger for Kafka.

### Workshop 9 Part A — Topics, producer, and event payload

This block sets up the three Kafka topics declaratively, builds the producer that publishes trade events keyed by `tradeRef`, and defines the event record itself. The goal is a clean, reproducible topic layout on boot and a producer that preserves per-trade ordering.

**Why this block matters:** topics and the event payload are the *contract* between every producer and every consumer for the rest of the day. If `TradeEvent` is the wrong shape, every downstream consumer has to be rewritten. If the topic isn't partitioned correctly, you lose per-trade ordering and recon will see updates out of sequence. Get the bones right here and the next three blocks become straightforward; get them wrong and you will be re-running the broker between every ticket. Keying by `tradeRef` is what guarantees all events for the same trade land on the same partition and are therefore processed in order — that single design decision is the foundation for everything from the DLQ retry strategy to the event-sourced rebuild.

---

### TICKET-ADV128 — Kafka topics (trade-events, recon-results, system-alerts)

**Goal:** Declare the three primary Kafka topics plus the trade-events DLQ topic so the broker creates them automatically on application startup.

**What**
- A `KafkaTopicsConfig.java` `@Configuration` class declaring four `NewTopic` beans: `trade-events` (3P), `trade-events-dlq` (3P), `recon-results` (2P), `system-alerts` (1P), each with replication factor 1, plus `public static final String` constants for every topic name.

**Why**
- Every downstream ticket on this day (ADV129's producer, ADV131/132/133/136 consumers, ADV134's DLQ recoverer) wires by topic name, and ADV134's `record.topic() + "-dlq"` resolver fails with `UNKNOWN_TOPIC_OR_PARTITION` if `trade-events-dlq` was not pre-declared here.

**Observe**
- After backend restart, Kafdrop at `http://localhost:9000` lists all four topics with partition counts 3 / 3 / 2 / 1 and no manual `kafka-topics.sh` was executed.

**Done when:**
- A `@Configuration` class declares four `NewTopic` beans: `trade-events` (3 partitions), `trade-events-dlq` (3 partitions), `recon-results` (2 partitions), `system-alerts` (1 partition), each with `replicas(1)`.
- After restarting the backend, Kafdrop at `http://localhost:9000` shows all four topics with the correct partition counts, and no manual `kafka-topics.sh` command was run.
- Topic names are exposed as `public static final String` constants on the configuration class so the producer and consumers can reference them without typo risk.

<details>
<summary>Hint 1 — gentle direction</summary>

How does Kafka know a topic exists when your producer first sends to it? You have two options: rely on the broker's auto-create behaviour, or have your application declare topics on startup. Auto-create is fine in dev but quietly creates wrong-shaped topics in production. The declarative path is reproducible across dev, CI, and demo machines — and it documents your topic layout in code, where future-you can find it.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Spring Kafka auto-configures a `KafkaAdmin` bean. That bean picks up every `org.apache.kafka.clients.admin.NewTopic` bean in the application context at startup and creates the topic on the broker if it does not yet exist. The builder you want is `org.springframework.kafka.config.TopicBuilder`. Put the four beans in `backend/src/main/java/com/dbtraining/reconx/config/KafkaTopicsConfig.java`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Four `@Bean` methods, each returning a `NewTopic`. Use `TopicBuilder.name(...).partitions(N).replicas(1).build()`. Partition counts: 3 for `trade-events`, 3 for `trade-events-dlq` (must match the main topic so the DLQ recoverer can preserve partition number), 2 for `recon-results`, 1 for `system-alerts` (strict global ordering). Add the four topic names as `public static final String` constants on the class.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/config/KafkaTopicsConfig.java` annotated `@Configuration`.
2. Declare four `public static final String` topic-name constants: `TRADE_EVENTS`, `TRADE_EVENTS_DLQ`, `RECON_RESULTS`, `SYSTEM_ALERTS`.
3. Add one `@Bean` method per topic returning `NewTopic`, built via `TopicBuilder.name(...).partitions(N).replicas(1).build()`.
4. Partition counts: `trade-events`=3, `trade-events-dlq`=3 (must equal main), `recon-results`=2, `system-alerts`=1.
5. Restart the backend and open Kafdrop at `http://localhost:9000` — verify all four topics with correct partition counts.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/KafkaTopicsConfig.java`):

```java
package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * ============================================================================
 * Declare Kafka topics on app start (3 main topics + DLQ)
 * DLQ topic must be pre-declared for DeadLetterPublishingRecoverer
 *
 * WHAT:    Creates 4 topics if they don't already exist.
 * HOW:     NewTopic beans are picked up by KafkaAdmin which creates them.
 * WHY:     Auto-create in code keeps `compose up` -> ready in one step.
 * OBSERVE: Kafdrop (http://localhost:9000) lists all 4 topics after startup.
 * ============================================================================
 */
@Configuration
@Profile("!dev & !test")
public class KafkaTopicsConfig {

    @Bean public NewTopic tradeEvents() {
        return TopicBuilder.name("trade-events").partitions(3).replicas(1).build();
    }

    @Bean public NewTopic reconResults() {
        return TopicBuilder.name("recon-results").partitions(2).replicas(1).build();
    }

    @Bean public NewTopic systemAlerts() {
        return TopicBuilder.name("system-alerts").partitions(1).replicas(1).build();
    }

    @Bean public NewTopic tradeEventsDlq() {
        return TopicBuilder.name("trade-events-dlq").partitions(3).replicas(1).build();
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV128 end-to-end**

Boot the broker + the app, then confirm all four topics exist with the right partition counts.

```bash
docker compose up -d kafka zookeeper kafdrop
./mvnw spring-boot:run
# Kafdrop UI: http://localhost:9000
```

**Observe:**

- `trade-events` listed with 3 partitions, replication factor 1.
- `trade-events-dlq` listed with 3 partitions (must match the main topic so the DLQ recoverer preserves partition numbers).
- `recon-results` with 2 partitions; `system-alerts` with 1 partition.
- Failure signal: a topic missing or showing the wrong partition count means `KafkaAdmin` did not pick up the `@Bean` — re-check `@Profile("!dev & !test")` and that the class is on the classpath.

---

### TICKET-ADV129 — TradeEventProducer

**Goal:** Build a producer component that publishes `TradeEvent` messages to the `trade-events` topic, keyed by `tradeRef` so all events for the same trade preserve order on one partition.

**What**
- A `TradeEventProducer` `@Component` exposing `publish(TradeEvent event)` that sends to `trade-events` keyed by `event.tradeRef()` and logs failures via `whenComplete` instead of `.get()`.

**Why**
- Per-`tradeRef` partition affinity is what lets ADV131's `ReconciliationConsumer` and ADV132's `AuditEventConsumer` process `TRADE_CREATED` before `TRADE_UPDATED` for the same trade; without keyed sends, ADV137's event-sourcing rebuild would replay events out of order.

**Observe**
- Publishing one event makes Kafdrop's `trade-events` topic view show the record on exactly one of the three partitions, with the message key equal to the tradeRef.

**Done when:**
- A `@Component` named `TradeEventProducer` exposes a single public method, `publish(TradeEvent event)`, which sends to the `trade-events` topic.
- The Kafka message key is `event.tradeRef()` — every event for the same trade lands on the same partition.
- The send completes asynchronously; failures are logged via the future's `whenComplete` callback, not by blocking on `.get()`.
- After invoking `publish` from a test or REST handler, Kafdrop shows the message under the `trade-events` topic with the expected partition assignment.

<details>
<summary>Hint 1 — gentle direction</summary>

Kafka assigns a record to a partition via `hash(key) % partitionCount`. Pick the right key and you get per-aggregate ordering for free. Pick the wrong key (or no key at all) and a `TRADE_UPDATED` event can be consumed before its `TRADE_CREATED`. Which field of the event identifies the aggregate — the unique event id, or the trade reference? Only one of those gives you the ordering you actually need.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

You want a `@Component` that takes a `org.springframework.kafka.core.KafkaTemplate<String, TradeEvent>` via constructor injection. The send method on `KafkaTemplate` takes `(topic, key, value)` and returns a `CompletableFuture<SendResult<...>>`. Do not call `.get()` on that future in the REST controller path — you will add 50–200 ms of blocking wait per request. Use `whenComplete` to log success or failure.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Constructor takes `KafkaTemplate<String, TradeEvent>`. The `publish(TradeEvent event)` method calls `template.send(TRADE_EVENTS, event.tradeRef(), event)` and chains `.whenComplete((result, ex) -> ...)` to log either the failure (with eventId and tradeRef) or the success (with partition and offset from `result.getRecordMetadata()`). No return value, no `.get()`, no `@Transactional`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/kafka/TradeEventProducer.java` annotated `@Component`.
2. Constructor-inject `KafkaTemplate<String, TradeEvent>`; declare a private `Logger`.
3. Import the `TRADE_EVENTS` constant statically from `KafkaTopicsConfig`.
4. Implement `publish(TradeEvent event)` calling `template.send(TRADE_EVENTS, event.tradeRef(), event)`.
5. Chain `.whenComplete((result, ex) -> ...)` to log success (partition + offset) or failure (eventId + tradeRef).
6. Do not call `.get()` on the future and do not annotate with `@Transactional`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/TradeEventProducer.java`):

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TradeEventProducer
 *
 * WHAT:    Publishes TradeEvent to the trade-events topic.
 * HOW:     Key = tradeRef (so all events for a single trade go to the same
 *          partition and preserve ordering).
 * WHY:     Out-of-order events for the same trade would make event sourcing
 *          impossible.
 * OBSERVE: Kafdrop → trade-events topic shows one message per published event,
 *          partitioned by tradeRef.
 * ============================================================================
 */
@Component
public class TradeEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventProducer.class);
    private static final String TOPIC = "trade-events";

    private final KafkaTemplate<String, TradeEvent> template;

    public TradeEventProducer(KafkaTemplate<String, TradeEvent> template) {
        this.template = template;
    }

    public void publish(TradeEvent event) {
        log.debug("Publishing TradeEvent eventId={} ref={} type={}",
                event.eventId(), event.tradeRef(), event.eventType());
        template.send(TOPIC, event.tradeRef(), event);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV129 end-to-end**

Trigger the producer by posting a new trade, then inspect the message in Kafdrop.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV129-A","instrument":"USD/EUR","notional":1000000,"side":"BUY"}'
# Kafdrop → topics → trade-events → Messages
```

**Observe:**

- A new event lands on a partition determined by the `tradeRef` hash (rerun with a different `tradeRef` and verify the partition can change).
- Sending another event with the same `tradeRef` lands on the same partition (per-trade ordering preserved).
- Failure signal: a `whenComplete` log line with an exception means the producer template never reached the broker — check `spring.kafka.bootstrap-servers`.

---

### TICKET-ADV130 — TradeEvent payload

**Goal:** Define the immutable Kafka event payload as a Java record carrying both event metadata and a before/after trade snapshot.

**What**
- An immutable `TradeEvent` Java `record` in the `dto` package with `eventId`, `tradeRef`, `eventType`, `timestamp`, `before`, `after`, a nested `EventType` enum (`TRADE_CREATED`, `TRADE_UPDATED`, `TRADE_CANCELLED`), and three static factories `created` / `updated` / `cancelled`.

**Why**
- ADV132 persists this payload into `audit_log`, and ADV137's `TradeAggregator.rebuild()` folds these same records back into current state, so the schema chosen here is the contract for the entire event-sourcing path.

**Observe**
- Jackson round-trip (publish then consume) of a `TRADE_UPDATED` event preserves both `before` and `after` `JsonNode` snapshots intact when read off `trade-events` via Kafdrop.

**Done when:**
- A Java `record` called `TradeEvent` in the `dto` package has these fields: `UUID eventId`, `String tradeRef`, `EventType eventType`, `Instant timestamp`, `JsonNode before`, `JsonNode after`.
- `EventType` is a nested enum with three values: `TRADE_CREATED`, `TRADE_UPDATED`, `TRADE_CANCELLED`.
- Three static factory methods exist: `created(tradeRef, after)`, `updated(tradeRef, before, after)`, `cancelled(tradeRef, before)`. Each generates a fresh `UUID` and an `Instant.now()`.
- Round-trip serialisation through Jackson (publish then consume) preserves all fields including the `JsonNode` snapshots.

<details>
<summary>Hint 1 — gentle direction</summary>

Three design questions to settle before you type. First, why a record rather than a class — what guarantees does immutability give you for an event? Second, why store the trade snapshot as `JsonNode` rather than as the `Trade` JPA entity — what happens if Jackson hits a lazy-loaded collection on a closed session? Third, why include the `before` snapshot at all when `after` is enough to rebuild current state — think about audit UX.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `java.time.Instant` for the timestamp (UTC, no timezone ambiguity), `java.util.UUID` for the event id, and `com.fasterxml.jackson.databind.JsonNode` for the before/after blobs. Place the record in `backend/src/main/java/com/dbtraining/reconx/dto/TradeEvent.java`. Spring Kafka's `JsonSerializer` handles records out of the box — you do not need a custom serializer.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`public record TradeEvent(UUID eventId, String tradeRef, EventType eventType, Instant timestamp, JsonNode before, JsonNode after) { ... }`. Inside the record, declare `public enum EventType { TRADE_CREATED, TRADE_UPDATED, TRADE_CANCELLED }` and three static factories. `created` passes `null` for `before`; `cancelled` passes `null` for `after`; `updated` passes both. No setters. No mutable collections.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/dto/TradeEvent.java` as a `public record`.
2. Add six fields: `UUID eventId`, `String tradeRef`, `EventType eventType`, `Instant timestamp`, `JsonNode before`, `JsonNode after`.
3. Nest a `public enum EventType { TRADE_CREATED, TRADE_UPDATED, TRADE_CANCELLED }`.
4. Add three static factory methods: `created(tradeRef, after)`, `updated(tradeRef, before, after)`, `cancelled(tradeRef, before)`.
5. Each factory generates a fresh `UUID.randomUUID()` and `Instant.now()`; pass `null` for the absent snapshot.
6. Verify Jackson round-trips by publishing then consuming a test event.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/dto/TradeEvent.java`):

```java
package com.dbtraining.reconx.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * TradeEvent payload (Kafka envelope)
 *
 * WHAT:    Wire format for trade-events Kafka topic. eventId is the
 *          idempotency key; consumers deduplicate by it.
 * HOW:     Record — Jackson serialises automatically (component model
 *          = default). before/after are JSON strings (not objects) to keep
 *          the contract resilient to entity refactors.
 * WHY:     Including before+after on every event makes downstream consumers
 *          (audit, recon) self-contained — they don't have to fetch the
 *          current state from the DB.
 * ============================================================================
 */
public record TradeEvent(
        UUID eventId,
        String tradeRef,
        EventType eventType,
        Instant timestamp,
        String actor,
        String before,
        String after
) {
    public enum EventType {
        TRADE_CREATED, TRADE_UPDATED, TRADE_CANCELLED
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV130 end-to-end**

Post a trade and read the raw payload off the wire to confirm the JSON contract.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV130-B","instrument":"GBP/USD","notional":500000,"side":"SELL"}'
# Kafdrop → trade-events → open the latest message
```

**Observe:**

- Payload is JSON containing `tradeRef`, `eventType`, `before`, `after`, `actor`, `timestamp`.
- `before` is `null` on create; `after` carries the full trade snapshot.
- Failure signal: payload printed as `[B@...` (byte array toString) means the JSON serialiser is not configured — re-check `value.serializer = JsonSerializer.class` on the `ProducerFactory`.

---

### Workshop 9 Part B — Consumers: recon, audit, and alert

Three `@KafkaListener` consumers, each in its own consumer group. Different groups means each consumer independently sees every message — that is the fan-out you want. Same group means load-balancing across consumer instances. The single most common mistake in this block is putting all three consumers in the same group and wondering why only one of them reacts to each event.

**Why this block matters:** this is where the architectural payoff from Part A actually shows up. Three consumer groups means three independent SLAs (slow recon doesn't block fast alerts), three independent failure stories (audit can be down without losing trade events — they queue on the broker), and three independent scaling stories (you can run five recon workers but only one audit writer). If you collapsed these into one consumer doing `switch (event.type)`, you would be back to the synchronous god-controller you left behind on Day 8 — just with extra Kafka ceremony. The whole point of fan-out is that each downstream concern *gets to fail or scale on its own terms*.

---

### TICKET-ADV131 — ReconciliationConsumer

**Goal:** Wire a consumer on the `trade-events` topic that triggers reconciliation logic for every event, in the `recon-service` consumer group.

**What**
- A `ReconciliationConsumer` `@Component` with `@KafkaListener(topics = "trade-events", groupId = "recon-service")` dispatching on `eventType` to `reconEngine.scheduleRecon(tradeRef)` for `TRADE_CREATED` / `TRADE_UPDATED` and `cancelPendingRecon(tradeRef)` for `TRADE_CANCELLED`.

**Why**
- This is the fan-out partner of ADV132's `audit-service` group; distinct group IDs are what guarantee both consumers see every event, and Day 6's `recon_break_count` gauge advances only when this listener fires.

**Observe**
- Kafdrop's Consumers view shows the `recon-service` group registered with assignment across all three partitions of `trade-events` after boot.

**Done when:**
- A `@Component` called `ReconciliationConsumer` has a `@KafkaListener(topics = "trade-events", groupId = "recon-service", ...)` method that receives a `TradeEvent`.
- The method dispatches on `event.eventType()`: `TRADE_CREATED` and `TRADE_UPDATED` call `reconEngine.scheduleRecon(tradeRef)`; `TRADE_CANCELLED` calls `reconEngine.cancelPendingRecon(tradeRef)`.
- After application boot, Kafdrop's Consumers view shows the `recon-service` group registered with assignment across all three partitions of `trade-events`.
- Publishing one event triggers exactly one recon dispatch, confirmed via log line or repository count.

<details>
<summary>Hint 1 — gentle direction</summary>

The exam question for this exercise is: two different consumers, two different consumer groups, same topic — what does each consumer see? Sketch it on paper before you start. If you cannot draw the arrows correctly, you will end up with the recon consumer and the audit consumer accidentally sharing partitions, and only one of them will react to each event. Consumer groups are the unit of parallelism and of fan-out.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

You want `@org.springframework.kafka.annotation.KafkaListener` on a method. The three parameters that matter are `topics`, `groupId`, and `containerFactory`. The trainer-provided container factory is named `tradeEventListenerContainerFactory` — use it so the JSON deserializer and error handler get wired in automatically. The method's single parameter should be `TradeEvent`. Place the consumer in `backend/src/main/java/com/dbtraining/reconx/kafka/ReconciliationConsumer.java`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`@Component` class, constructor takes `ReconciliationEngine`. Listener method annotated `@KafkaListener(topics = "trade-events", groupId = "recon-service", containerFactory = "tradeEventListenerContainerFactory")`. Inside, a `switch (event.eventType())` with two arms: the create/update arm calls `reconEngine.scheduleRecon(event.tradeRef())`, the cancelled arm calls `reconEngine.cancelPendingRecon(event.tradeRef())`. Do not add `@Async`. Do not throw a `RuntimeException` for unknown types yet — the error handler is not built until TICKET-ADV134.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/kafka/ReconciliationConsumer.java` annotated `@Component`.
2. Constructor-inject `ReconciliationEngine`; declare a private `Logger`.
3. Annotate the listener method `@KafkaListener(topics = "trade-events", groupId = "recon-service", containerFactory = "tradeEventListenerContainerFactory")`.
4. Take a single `TradeEvent event` parameter; log at debug with event type and tradeRef.
5. Switch on `event.eventType()`: `TRADE_CREATED`/`TRADE_UPDATED` call `reconEngine.scheduleRecon(...)`, `TRADE_CANCELLED` calls `reconEngine.cancelPendingRecon(...)`.
6. Boot and confirm Kafdrop shows `recon-service` group assigned across all three `trade-events` partitions.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/ReconciliationConsumer.java`):

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for trade-events and schedules reconciliation.
 *
 * In a full implementation this would push a row into recon_jobs and
 * trigger the engine. The trainer copy logs the trigger so students can
 * trace the message flow end-to-end without a full job runner.
 */
@Component
public class ReconciliationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationConsumer.class);

    @KafkaListener(topics = "trade-events", groupId = "recon-service")
    public void onTradeEvent(TradeEvent event) {
        log.info("Recon-trigger received eventId={} ref={} type={}",
                event.eventId(), event.tradeRef(), event.eventType());
        // TICKET-ADV131 (full impl) — enqueue a recon job; the trainer guide
        // explains why we schedule rather than reconcile inline (avoid
        // blocking the consumer thread).
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV131 end-to-end**

POST a trade and watch the recon consumer group catch up.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV131-C","instrument":"USD/JPY","notional":750000,"side":"BUY"}'
# Kafdrop → Consumer Groups → recon-service
```

**Observe:**

- Backend log line "ReconciliationConsumer received TradeEvent..." with the matching `tradeRef`.
- Kafdrop shows the `recon-service` consumer group with **lag = 0** across all 3 partitions of `trade-events`.
- Failure signal: lag stays > 0 — consumer thread is blocked or `containerFactory` is misconfigured. Check the `tradeEventListenerContainerFactory` bean and that `groupId = "recon-service"`.

---

### TICKET-ADV132 — AuditEventConsumer

**Goal:** Persist every `TradeEvent` to the `audit_log` table (built on Day 1) using a separate consumer group, so the event log becomes the foundation of the event-sourcing rebuild in TICKET-ADV137.

**What**
- An `AuditEventConsumer` `@Component` with `@KafkaListener(topics = "trade-events", groupId = "audit-service")`, annotated `@Transactional`, that builds an `AuditLogEntry` from each `TradeEvent` (operation = `eventType.name()`, before/after snapshots, `occurredAt = event.timestamp()`) and saves via `AuditLogRepository`.

**Why**
- The `audit_log` table written here (built on Day 1) is the source of truth that ADV137's `TradeAggregator` reads to rebuild trade state and that ADV138's admin audit endpoint serves -- distinct group from ADV131 so both fire on every event.

**Observe**
- After publishing 10 events for one `tradeRef`, `SELECT COUNT(*) FROM audit_log WHERE trade_ref = ?` returns exactly 10 in event-time order.

**Done when:**
- A `@Component` called `AuditEventConsumer` has a `@KafkaListener(topics = "trade-events", groupId = "audit-service", ...)` method.
- Inside the method, an `AuditLogEntry` is built from the event (`eventId`, `tradeRef`, `operation = eventType.name()`, `beforeData`, `afterData`, `occurredAt = event.timestamp()`) and saved via `AuditLogRepository`.
- The listener method is annotated `@Transactional` so the DB write is bound to the listener invocation.
- After publishing 10 events for one trade, the `audit_log` table contains exactly 10 rows for that trade in event-time order.

<details>
<summary>Hint 1 — gentle direction</summary>

The audit consumer has to be in a different consumer group from the recon consumer. Why? If they shared a group, Kafka would split the partitions between them and each event would land on either recon or audit — never both. Fan-out requires distinct group ids. The other design point: should the listener method be transactional? Think about what happens if the DB write succeeds but the offset commit crashes immediately afterwards.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Constructor inject `AuditLogRepository`. Use `groupId = "audit-service"` and the same `containerFactory = "tradeEventListenerContainerFactory"` as the recon consumer. The `AuditLogEntry` builder is already in place from Day 1 — chain `.eventId(...).tradeRef(...).operation(...).beforeData(...).afterData(...).occurredAt(...).build()`. The repository is `JpaRepository`-based; `.save(entry)` is enough.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`@Component` class. `@KafkaListener(topics = "trade-events", groupId = "audit-service", containerFactory = "tradeEventListenerContainerFactory")` plus `@Transactional` on the method. Method body: build an `AuditLogEntry` from the event fields (operation = `event.eventType().name()`), call `auditRepo.save(entry)`, log at debug with eventId and tradeRef. Note that `operation` is a `String` column — convert the enum via `.name()`, not via reflection.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/kafka/AuditEventConsumer.java` annotated `@Component`.
2. Constructor-inject `AuditLogRepository`; declare a private `Logger`.
3. Annotate the listener method `@KafkaListener(topics = "trade-events", groupId = "audit-service", containerFactory = "tradeEventListenerContainerFactory")` plus `@Transactional`.
4. Build an `AuditLogEntry` from the event via the builder (eventId, tradeRef, `operation = event.eventType().name()`, before, after, `occurredAt = event.timestamp()`).
5. Save through `auditRepo.save(entry)` and log at debug with eventId and tradeRef.
6. Publish 10 events for one trade and verify exactly 10 rows in `audit_log`.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/AuditEventConsumer.java`):

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * AuditEventConsumer: persist every TradeEvent to audit_log.
 * Together with TICKET-ADV137 this powers event-sourced replay.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);
    private final AuditLogRepository repo;

    public AuditEventConsumer(AuditLogRepository repo) { this.repo = repo; }

    @KafkaListener(topics = "trade-events", groupId = "audit-service")
    public void onTradeEvent(TradeEvent e) {
        repo.save(new AuditLogEntry(
                e.eventId().toString(),
                e.tradeRef(),
                e.eventType().name(),
                e.timestamp(),
                e.actor(),
                e.before(),
                e.after()
        ));
        log.debug("Audit row persisted for eventId={}", e.eventId());
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV132 end-to-end**

POST a trade, then read the `audit_log_entries` table to confirm the audit consumer ran independently of recon.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV132-D","instrument":"EUR/CHF","notional":250000,"side":"SELL"}'
curl -s 'http://localhost:8080/actuator/health'
# Inspect DB: psql or H2 console — select count(*) from audit_log_entries;
```

**Observe:**

- AuditEventConsumer log line fires once per event.
- A new row appears in `audit_log_entries` with the event payload.
- Kafdrop → Consumer Groups shows **both** `recon-service` and `audit-service` at offset N (different groups, each saw the same event).
- Failure signal: only one group visible — both consumers are sharing a `groupId`. Distinct `groupId` strings are mandatory for fan-out.

---

### TICKET-ADV133 — AlertConsumer

**Goal:** Listen on the `system-alerts` topic and surface every alert at WARN level, with an optional pluggable sink for Slack or another notifier.

**What**
- An `AlertConsumer` `@Component` listening on `system-alerts` (groupId `alert-service`) that logs each `SystemAlert` at WARN with severity / code / message and forwards to a constructor-injected `AlertSink` (default `NoopAlertSink`).

**Why**
- The 1-partition design preserves strict global ordering of alerts so an OPS-NEW followed by OPS-RESOLVED never inverts; ADV142's Grafana DLQ alert pipeline reuses this same single-partition discipline.

**Observe**
- Kafdrop's Consumers view registers `alert-service` on the single partition of `system-alerts`, and backend logs emit a `WARN ALERT` line per published `SystemAlert`.

**Done when:**
- A `@Component` called `AlertConsumer` has a `@KafkaListener(topics = "system-alerts", groupId = "alert-service", ...)` method receiving a `SystemAlert` DTO.
- Each received alert is logged at WARN with severity, code, and message.
- The consumer also calls a constructor-injected `AlertSink.notify(alert)` so a real implementation can be plugged in later. The starter provides `NoopAlertSink`; leave that in place unless you have a Slack webhook handy.
- The `system-alerts` topic has a single partition — verify in Kafdrop that consumer group `alert-service` is registered on that one partition.

<details>
<summary>Hint 1 — gentle direction</summary>

Why is `system-alerts` a single-partition topic? Because the audience cares about strict global ordering: alert 42 must be seen before alert 43, always. A multi-partition topic could deliver them out of order across partitions. The trade-off is that single-partition topics cap consumer parallelism at one — but for alerts, ordering beats throughput.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

This consumer needs its own container factory because it deserialises a different value type (`SystemAlert`, not `TradeEvent`). Use the trainer-provided `systemAlertListenerContainerFactory`. Inject `AlertSink` via constructor (the starter wires `NoopAlertSink` in the bean graph). Place the file at `backend/src/main/java/com/dbtraining/reconx/kafka/AlertConsumer.java`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`@Component` class with constructor-injected `AlertSink`. `@KafkaListener(topics = "system-alerts", groupId = "alert-service", containerFactory = "systemAlertListenerContainerFactory")` on a method taking `SystemAlert alert`. Method body: one `log.warn(...)` line with severity, code, and message, then `sink.notify(alert)`. No transaction needed — alerts are fire-and-forget.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/kafka/AlertConsumer.java` annotated `@Component`.
2. Constructor-inject `AlertSink` (the starter wires `NoopAlertSink`); declare a private `Logger`.
3. Annotate the listener method `@KafkaListener(topics = "system-alerts", groupId = "alert-service", containerFactory = "systemAlertListenerContainerFactory")`.
4. Take a single `SystemAlert alert` parameter and log at WARN with severity, code, and message.
5. Delegate to `sink.notify(alert)` for the pluggable side effect.
6. Verify in Kafdrop that consumer group `alert-service` is registered on the single `system-alerts` partition.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/AlertConsumer.java`):

```java
package com.dbtraining.reconx.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** AlertConsumer: log + (in real prod) notify Slack/Pager. */
@Component
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    @KafkaListener(topics = "system-alerts", groupId = "alert-service")
    public void onAlert(String payload) {
        log.warn("ALERT: {}", payload);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV133 end-to-end**

POST a trade and confirm three distinct consumer groups are now visible.

```bash
curl -X POST http://localhost:8080/api/v1/trades \
  -H 'Content-Type: application/json' \
  -d '{"tradeRef":"T-ADV133-E","instrument":"AUD/USD","notional":600000,"side":"BUY"}'
# Kafdrop → Consumer Groups
```

**Observe:**

- AlertConsumer log line `ALERT: ...` fires.
- Kafdrop → Consumer Groups lists **three** groups: `recon-service`, `audit-service`, `alert-service`, each at offset N with lag 0.
- Failure signal: only two groups visible — the alert consumer never ran. Verify the `@KafkaListener` annotation lives on a Spring-managed `@Component` and the class is component-scanned.

---

### Workshop 9 Part C — DLQ, retry, replay, and event sourcing

The meatiest block of the day. By the end you will have a `DefaultErrorHandler` that retries three times with exponential backoff, a Dead-Letter Publishing Recoverer that lands failures in `trade-events-dlq` on the same partition number, a DLQ consumer that records the failure context, an admin replay endpoint that re-publishes a single message back to the main topic, and a `TradeAggregator` that rebuilds any trade's state from its event history.

**Why this block matters:** this is where Kafka stops being "REST with an extra step" and starts earning its production reputation. **Retry with backoff** lets you survive transient failures (a downstream DB blip, a network hiccup) without a human waking up. **DLQ** lets one bad message be quarantined while the good ones keep flowing — that is the difference between "one corrupt trade broke recon for the whole desk" and "one trade is in DLQ for ops to look at on Monday". **Replay** is the manual override: once you have the bug fix in, you point the DLQ consumer at the bad partition and re-feed the original message. And **event sourcing rebuild** is the auditor's favourite property: even if the `trades` table is dropped or corrupted, the event log still contains the entire history and a `TradeAggregator` can reconstruct every row by replaying from offset 0. Skip any of these and you have a fragile pipeline; ship all four and you have something an SRE will defend.

---

### TICKET-ADV134 — Dead-Letter Queue wiring

**Goal:** Configure a `DefaultErrorHandler` that publishes records to a `*-dlq` topic on the same partition number after retries are exhausted.

**What**
- A `KafkaErrorHandlerConfig.java` `@Configuration` declaring a `DefaultErrorHandler` backed by a `DeadLetterPublishingRecoverer` whose destination resolver maps each failed record to `new TopicPartition(record.topic() + "-dlq", record.partition())`, wired into the trade-events container factory, with deserialization exceptions added to the not-retryable list.

**Why**
- Without this, ADV135's retry budget runs forever on poison pills, ADV136's DLQ consumer has nothing to consume, and ADV142's `KafkaDlqMessages` Grafana alert never fires -- this is the linchpin of the error-handling chain.

**Observe**
- After a listener throws three times, Kafdrop shows the failing record on `trade-events-dlq` on the same partition number it occupied in `trade-events`.

**Done when:**
- A `@Configuration` class declares a `DefaultErrorHandler` bean built from a `DeadLetterPublishingRecoverer`.
- The recoverer's destination resolver maps each failed record to a new `TopicPartition` with name `record.topic() + "-dlq"` and the same `record.partition()`, so the DLQ preserves the original partition number.
- The container factory used by the trade-events listeners is wired to this error handler.
- Deserialization-class exceptions are added to the not-retryable list, so a poison-pill message goes straight to DLQ without three pointless retries.

<details>
<summary>Hint 1 — gentle direction</summary>

There are two wrong paths here. The first is wrapping the listener method body in `try/catch` and manually publishing to the DLQ inside the catch block — it works but bypasses the retry/backoff machinery and you cannot reuse it across consumers. The second is forgetting to declare the DLQ topic in TICKET-ADV128 (`trade-events-dlq`), so the recoverer's produce fails with `UNKNOWN_TOPIC_OR_PARTITION` and the message vanishes. Confirm both before you start.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The classes you need: `org.springframework.kafka.listener.DefaultErrorHandler`, `org.springframework.kafka.listener.DeadLetterPublishingRecoverer`, and `org.springframework.util.backoff.ExponentialBackOff`. The recoverer takes a `KafkaTemplate<String, Object>` and a `BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition>` that decides where to publish the failed record. Save the new config in `backend/src/main/java/com/dbtraining/reconx/config/KafkaErrorHandlerConfig.java`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

`@Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template)`. Inside: build the recoverer with a destination resolver lambda taking `(record, ex)` and returning a new `TopicPartition(record.topic() + "-dlq", record.partition())`. Construct an `ExponentialBackOff(1000L, 2.0)` and call `setMaxElapsedTime(8_000L)`. Pass both into `new DefaultErrorHandler(recoverer, backOff)`. Call `handler.addNotRetryableExceptions(DeserializationException.class, IllegalArgumentException.class)`. Return the handler.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/config/KafkaErrorHandlerConfig.java` annotated `@Configuration`.
2. Declare `@Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template)`.
3. Inside, build a `DeadLetterPublishingRecoverer` with destination resolver `(record, ex) -> new TopicPartition(record.topic() + "-dlq", record.partition())`.
4. Construct `ExponentialBackOff(1000L, 2.0)` and call `setMaxElapsedTime(8_000L)`.
5. Pass recoverer + backoff to `new DefaultErrorHandler(...)`, then `addNotRetryableExceptions(DeserializationException.class, IllegalArgumentException.class)`.
6. Wire this handler into the trade-events listener container factory; verify a thrown listener routes to `trade-events-dlq` on the same partition.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/KafkaErrorHandlerConfig.java`):

```java
package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * ============================================================================
 * DLQ via DeadLetterPublishingRecoverer (failed messages
 *                routed to {topic}-dlq with the same partition number)
 * Retry strategy: 3 attempts with exponential backoff
 *                (1s, 2s, 4s) before giving up to DLQ
 * ============================================================================
 */
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (ConsumerRecord<?, ?> rec, Exception ex) ->
                        new TopicPartition(rec.topic() + "-dlq", rec.partition())
        );
        ExponentialBackOff backoff = new ExponentialBackOff(1000L, 2.0);
        backoff.setMaxAttempts(3);
        return new DefaultErrorHandler(recoverer, backoff);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV134 end-to-end**

Produce a deliberately malformed event via Kafdrop's "Produce" tab (or a curl against a debug endpoint) and watch the DLQ catch it.

```bash
# Kafdrop → trade-events → Produce → send raw text "this-is-not-json"
# Then watch backend logs and trade-events-dlq
```

**Observe:**

- Backend log shows 3 retry attempts on the same offset.
- After retries exhaust, the message lands on `trade-events-dlq` on the **same partition** it arrived on in `trade-events`.
- Kafdrop → `trade-events-dlq` shows 1 new message; the headers (`kafka_dlt-*`) carry the original topic/partition/offset.
- Failure signal: message vanishes silently — DLQ topic missing or partition counts mismatched. Re-check TICKET-ADV128.

---

### TICKET-ADV135 — Retry strategy with exponential backoff

**Goal:** Tune the error handler's backoff so retries follow an exponential schedule (roughly 1s, 2s, 4s) and stop after about three attempts before the message lands in the DLQ.

**What**
- An `ExponentialBackOff` (initial 1000 ms, multiplier 2.0, `setMaxElapsedTime` ~8 s) wired into the `DefaultErrorHandler` so retries fire at ~1 s / 2 s / 4 s and then the recoverer publishes to DLQ.

**Why**
- Fixed backoff would hammer a struggling downstream and prolong outages; exponential gives the downstream time to recover, and this schedule is what ADV144's DLQ-routing test asserts the timing of.

**Observe**
- Backend logs around a forced failure show three `Retrying` entries with widening gaps (~1 s, ~2 s, ~4 s) followed by a single `Publishing to DLQ` line, and the consumer offset commits cleanly afterwards.

**Done when:**
- The `ExponentialBackOff` is constructed with initial interval `1000` ms and multiplier `2.0`.
- `setMaxElapsedTime` caps total retry wait at roughly 8 seconds, yielding three retries before the recoverer publishes to the DLQ.
- A test that throws a `RuntimeException` on every retry shows three retry attempts in the logs, then a DLQ publish, then the consumer moves on — offset commits, no infinite loop.
- You can describe in one sentence why you would not pick fixed backoff for this scenario.

<details>
<summary>Hint 1 — gentle direction</summary>

Three backoff shapes to understand: fixed (wait N seconds every retry — predictable but hammers a slow downstream), exponential (1s, 2s, 4s, 8s — gives the downstream time to recover), and exponential-with-jitter (same shape but randomised — prevents thundering herd when a thousand consumers all retry at second four). Spring Kafka's `ExponentialBackOff` does not add jitter by default; that is a known production gap, not a bug you need to fix today.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`ExponentialBackOff` lives in `org.springframework.util.backoff`. Two constructor arguments: the initial interval in milliseconds, and the multiplier. The setter that controls when retries stop is `setMaxElapsedTime(long)` — this is the total wait budget across all retries, not the per-attempt cap. Read the Javadoc once: the relationship between `initialInterval`, `multiplier`, and `maxElapsedTime` determines the retry count, so an 8000 ms budget with 1s initial and 2.0 multiplier gives you roughly three retries.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

This is configured inside the same `kafkaErrorHandler` bean method you built in TICKET-ADV134. Just confirm the three numbers: `new ExponentialBackOff(1000L, 2.0)` and `backOff.setMaxElapsedTime(8_000L)`. The attempt timeline should be: t=0 first try fails, t=1s retry 1 fails, t=3s retry 2 fails, t=7s retry 3 fails, recoverer publishes to DLQ.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Reopen `KafkaErrorHandlerConfig.java` from TICKET-ADV134.
2. Confirm the backoff constructor is `new ExponentialBackOff(1000L, 2.0)` — initial 1s interval, multiplier 2.0.
3. Confirm `backOff.setMaxElapsedTime(8_000L)` — total budget gives roughly three retries (1s + 2s + 4s).
4. Confirm the backoff is wired into the same `DefaultErrorHandler(recoverer, backOff)` constructor.
5. Run a test that throws on every retry and verify three log lines for retries followed by a single DLQ publish.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/KafkaErrorHandlerConfig.java` — same bean as TICKET-ADV134, backoff section highlighted):

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            template,
            (ConsumerRecord<?, ?> rec, Exception ex) ->
                    new TopicPartition(rec.topic() + "-dlq", rec.partition())
    );
    // 1s, 2s, 4s — three attempts total, then DLQ.
    ExponentialBackOff backoff = new ExponentialBackOff(1000L, 2.0);
    backoff.setMaxAttempts(3);
    return new DefaultErrorHandler(recoverer, backoff);
}
```

</details>

**▶ Run the project — verify TICKET-ADV135 end-to-end**

Produce a malformed event again and time the retry log lines.

```bash
# Kafdrop → trade-events → Produce → "this-is-not-json"
# Then: tail backend logs, note timestamps on each retry log line
```

**Observe:**

- Retry 1 fires at ~t+1s, retry 2 at ~t+3s (cumulative), retry 3 at ~t+7s.
- DLQ publish happens after ~7s total elapsed.
- Failure signal: retries fire back-to-back with no delay — the `ExponentialBackOff` instance is not being used. Confirm `new ExponentialBackOff(1000L, 2.0)` and that `setMaxElapsedTime(8_000L)` is set.

---

### TICKET-ADV136 — DLQ consumer and replay endpoint

**Goal:** Build a consumer on `trade-events-dlq` that records each failed message, plus an admin REST endpoint that re-publishes a single DLQ message back to the main topic.

**What**
- A `DlqConsumer` `@Component` on `trade-events-dlq` (groupId `dlq-monitor`) that persists a `DlqMessage` row per failure (with `eventId`, `originalTopic`, `partition`, `offset`, `payload`, `reason`, `firstSeen`), plus a `POST /api/v1/admin/dlq/replay` endpoint (query params `eventId` required, `dryRun` default false) protected by `@PreAuthorize("hasRole('ADMIN')")` that re-publishes via `TradeEventProducer` and deletes the DLQ row.

**Why**
- This is the operator's escape hatch when ADV134's DLQ catches real bugs in production; one-at-a-time replay by `eventId` prevents the bulk-replay anti-pattern, and the ADMIN role check echoes Day 5's RBAC contract.

**Observe**
- `POST /api/v1/admin/dlq/replay?eventId=...&dryRun=true` as an ADMIN returns the would-be payload; the same call as a TRADER returns 403; an unauthenticated call returns 401.

**Done when:**
- A `@Component` called `DlqConsumer` has a `@KafkaListener(topics = "trade-events-dlq", groupId = "dlq-monitor", ...)` method.
- The listener method receives both the `ConsumerRecord<String, TradeEvent>` and the `KafkaHeaders.EXCEPTION_MESSAGE` header, persists a `DlqMessage` row (with `eventId`, `tradeRef`, `originalTopic`, `partition`, `offset`, `payload`, `reason`, `firstSeen`), and logs at ERROR with full context.
- A `@RestController` at `POST /api/v1/admin/dlq/replay` accepts an `eventId` query parameter (required) and a `dryRun` boolean (default `false`). On dry-run, it returns what would be replayed without doing it. On real run, it re-publishes via `TradeEventProducer` and deletes the DLQ row.
- The endpoint is protected with `@PreAuthorize("hasRole('ADMIN')")`.

<details>
<summary>Hint 1 — gentle direction</summary>

Two design traps to avoid. First, do not write an endpoint that replays everything in the DLQ — if the underlying bug is not fixed, you will just re-DLQ the same messages and waste retry budget. Always replay one event at a time, by `eventId`. Second, this endpoint changes state, so it must be authenticated and authorised. RBAC matters even in dev: get used to writing `@PreAuthorize` now so it does not feel optional later.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

For the consumer: use `@Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMsg` to pull the failure reason that `DeadLetterPublishingRecoverer` writes into the record. The `ConsumerRecord` parameter gives you access to `.topic()`, `.partition()`, `.offset()`, `.value()`. For the controller: `@RestController` + `@RequestMapping("/api/v1/admin/dlq")` + `@PreAuthorize("hasRole('ADMIN')")` at class level. The replay endpoint is `@PostMapping("/replay")` with `@RequestParam UUID eventId` and `@RequestParam(defaultValue = "false") boolean dryRun`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Consumer: place at `backend/src/main/java/com/dbtraining/reconx/kafka/DlqConsumer.java`. Build a `DlqMessage` via its builder, setting `originalTopic = record.topic().replace("-dlq", "")`. Controller: place at `backend/src/main/java/com/dbtraining/reconx/controller/DlqAdminController.java`. Lookup by `repo.findByEventId(eventId).orElseThrow(...)`. On dryRun, return a `Map.of("dryRun", true, "wouldReplayTo", msg.getOriginalTopic(), ...)`. On real run, call `producer.publish(msg.getPayload())`, `repo.delete(msg)`, and return a confirmation map.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `DlqConsumer.java` annotated `@Component` with constructor-injected `DlqMessageRepository`.
2. Annotate the listener method `@KafkaListener(topics = "trade-events-dlq", groupId = "dlq-monitor", containerFactory = "tradeEventListenerContainerFactory")`.
3. Receive `ConsumerRecord<String, TradeEvent>` and `@Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMsg`; persist a `DlqMessage` row with `originalTopic = record.topic().replace("-dlq", "")`.
4. Create `DlqAdminController.java` at `/api/v1/admin/dlq`, class-level `@PreAuthorize("hasRole('ADMIN')")`, constructor-inject `DlqMessageRepository` and `TradeEventProducer`.
5. Implement `@PostMapping("/replay")` with `@RequestParam UUID eventId` and `@RequestParam(defaultValue = "false") boolean dryRun`.
6. Look up by `eventId`; on dryRun return a preview map; on real run call `producer.publish(msg.getPayload())`, delete the row, return a confirmation map.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/kafka/DlqConsumer.java`):

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.model.DlqMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqMessageRepository repo;

    public DlqConsumer(DlqMessageRepository repo) {
        this.repo = repo;
    }

    @KafkaListener(
            topics = "trade-events-dlq",
            groupId = "dlq-monitor",
            containerFactory = "tradeEventListenerContainerFactory"
    )
    public void onDlqMessage(ConsumerRecord<String, TradeEvent> record,
                             @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMsg) {
        TradeEvent event = record.value();
        log.error("DLQ: trade={} eventId={} reason={}",
                event.tradeRef(), event.eventId(), exMsg);

        repo.save(DlqMessage.builder()
                .eventId(event.eventId())
                .tradeRef(event.tradeRef())
                .originalTopic(record.topic().replace("-dlq", ""))
                .partition(record.partition())
                .offset(record.offset())
                .payload(event)
                .reason(exMsg)
                .firstSeen(Instant.now())
                .build());
    }
}
```

`backend/src/main/java/com/dbtraining/reconx/controller/DlqAdminController.java`:

```java
package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.model.DlqMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
public class DlqAdminController {

    private final DlqMessageRepository repo;
    private final TradeEventProducer producer;

    public DlqAdminController(DlqMessageRepository repo, TradeEventProducer producer) {
        this.repo = repo;
        this.producer = producer;
    }

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replay(
            @RequestParam UUID eventId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        DlqMessage msg = repo.findByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException("No DLQ message: " + eventId));

        if (dryRun) {
            return ResponseEntity.ok(Map.of(
                    "dryRun", true,
                    "wouldReplayTo", msg.getOriginalTopic(),
                    "tradeRef", msg.getTradeRef()
            ));
        }

        producer.publish(msg.getPayload());
        repo.delete(msg);

        return ResponseEntity.ok(Map.of(
                "replayed", true,
                "eventId", eventId,
                "topic", msg.getOriginalTopic()
        ));
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV136 end-to-end**

After a message lands on the DLQ (from ADV134/ADV135), call the replay endpoint and confirm it round-trips back through recon successfully.

```bash
# First find a DLQ message
curl -s 'http://localhost:8080/api/v1/admin/dlq' | jq
# Replay it
curl -X POST http://localhost:8080/api/v1/dlq/replay/0/0
```

**Observe:**

- Replay endpoint returns 200 with `{"replayed":true, ...}`.
- The original DLQ row is marked replayed in the admin listing.
- The recon consumer picks up the republished event on `trade-events` and processes it cleanly (log line, no further retries).
- Failure signal: replay returns 200 but recon never logs — the producer is targeting the wrong topic; check the `KafkaTemplate.send(TRADE_EVENTS, ...)` call.

---

### TICKET-ADV137 — Event sourcing rebuild

**Goal:** Build a `TradeAggregator` service whose `rebuild(String tradeRef)` method reads every event for that trade from `audit_log`, ordered by timestamp, and folds them into the current state.

**What**
- A `TradeAggregator` `@Service` with `Optional<JsonNode> rebuild(String tradeRef)` that reads `auditRepo.findByTradeRefOrderByOccurredAtAsc(tradeRef)` and folds events into running state (`TRADE_CREATED` / `TRADE_UPDATED` set state to `afterData`; `TRADE_CANCELLED` sets it to null), returning `Optional.empty()` when no events exist.

**Why**
- This is the payoff of the event log persisted by ADV132 -- proves the system can reconstruct any trade from its event stream alone, the canonical event-sourcing pattern; ADV138's admin endpoint exposes the same audit history this aggregator folds over.

**Observe**
- Calling `rebuild("TRD-001")` after a `CREATED` then `UPDATED` then `CANCELLED` sequence returns `Optional.empty()`; replaying without the `CANCELLED` returns the `after` snapshot of the last `UPDATED`.

**Done when:**
- A `@Service` called `TradeAggregator` has a method `Optional<JsonNode> rebuild(String tradeRef)`.
- It reads `AuditLogEntry` rows via `auditRepo.findByTradeRefOrderByOccurredAtAsc(tradeRef)`.
- For each event: `TRADE_CREATED` and `TRADE_UPDATED` set the running state to `entry.getAfterData()`; `TRADE_CANCELLED` sets it to `null`.
- The method returns `Optional.empty()` if there are no events, and `Optional.ofNullable(state)` otherwise — so a cancelled trade returns an empty Optional.

<details>
<summary>Hint 1 — gentle direction</summary>

Three traps. First, do not order by `eventId` — UUIDs are not monotonic and you will see events out of order. Order by `occurredAt` (or, in production, by Kafka offset within partition). Second, `TRADE_CANCELLED` has `after = null`. If you blindly assign `state = entry.getAfterData()` without checking the event type, you will lose state when an `UPDATED` event happens to have a null after-data field. Third, rebuilding on every read is fine for our scale; production systems snapshot every K events, but that is not your job today.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

`@Service` class in `backend/src/main/java/com/dbtraining/reconx/service/TradeAggregator.java`. Constructor inject `AuditLogRepository`. The repository method you want is `findByTradeRefOrderByOccurredAtAsc(String)` — confirm it already exists from Day 1; if not, add the derived query method to the repository interface. Use `TradeEvent.EventType.valueOf(entry.getOperation())` to convert the stored string back to the enum.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Method body: fetch the events list; if empty, return `Optional.empty()`. Declare a local `JsonNode state = null`. Iterate the events; switch on `TradeEvent.EventType.valueOf(e.getOperation())` with two arms — the create/update arm sets `state = e.getAfterData()`, the cancelled arm sets `state = null`. Return `Optional.ofNullable(state)`. No streams, no fancy folds — a simple for-loop is clearer for the audit story.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/service/TradeAggregator.java` annotated `@Service`.
2. Constructor-inject `AuditLogRepository`.
3. Implement `Optional<JsonNode> rebuild(String tradeRef)` that calls `auditRepo.findByTradeRefOrderByOccurredAtAsc(tradeRef)`.
4. Return `Optional.empty()` if the list is empty; otherwise iterate with a local `JsonNode state = null`.
5. Switch on `TradeEvent.EventType.valueOf(e.getOperation())`: `TRADE_CREATED`/`TRADE_UPDATED` set `state = e.getAfterData()`; `TRADE_CANCELLED` sets `state = null`.
6. Return `Optional.ofNullable(state)`; verify a sequence of created → updated → cancelled returns empty.

**Reference solution** (`backend/src/main/java/com/dbtraining/reconx/service/TradeAggregator.java`):

```java
package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.model.AuditLogEntry;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TradeAggregator {

    private final AuditLogRepository auditRepo;

    public TradeAggregator(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    public Optional<JsonNode> rebuild(String tradeRef) {
        List<AuditLogEntry> events = auditRepo.findByTradeRefOrderByOccurredAtAsc(tradeRef);
        if (events.isEmpty()) {
            return Optional.empty();
        }

        JsonNode state = null;
        for (AuditLogEntry e : events) {
            switch (TradeEvent.EventType.valueOf(e.getOperation())) {
                case TRADE_CREATED, TRADE_UPDATED -> state = e.getAfterData();
                case TRADE_CANCELLED              -> state = null;
            }
        }
        return Optional.ofNullable(state);
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV137 end-to-end**

Wipe the trades table, then rebuild from the event log and confirm every row comes back.

```bash
# Drop trades (psql)
psql -h localhost -U reconx -d reconx -c 'TRUNCATE trades CASCADE;'
# Trigger rebuild
curl -X POST http://localhost:8080/api/v1/admin/rebuild
psql -h localhost -U reconx -d reconx -c 'SELECT count(*) FROM trades;'
```

**Observe:**

- `trades` row count after rebuild matches the count before truncate.
- Backend log shows the aggregator folding events per `tradeRef`.
- Cancelled trades are absent (last event wins).
- Failure signal: count mismatch — the aggregator is reading events out of order. Confirm the repo call is `findByTradeRefOrderByEventTimestampAsc`.

---

### TICKET-ADV138 — Admin audit endpoint

**Goal:** Expose `GET /api/v1/audit/trades/{tradeRef}/events` so admins and recon analysts can fetch the full event history for any trade, oldest first.

**What**
- A `@RestController` at `/api/v1/audit/trades` with `@GetMapping("/{tradeRef}/events")` returning `List<TradeEvent>` ordered by timestamp ascending, protected by `@PreAuthorize("hasAnyRole('ADMIN','RECON_ANALYST')")`.

**Why**
- Recon analysts need this to debug breaks raised by Day 6's recon engine; admins need it for compliance evidence. The endpoint pairs with ADV137's `TradeAggregator` to give operators both the event log and the rebuilt state.

**Observe**
- `curl -H "Authorization: Bearer <ADMIN>" /api/v1/audit/trades/TRD-001/events` returns the ordered event list as JSON; the same call without a token returns 401, and a TRADER-roled token returns 403.

**Done when:**
- A `@RestController` at path `/api/v1/audit/trades` has a `@GetMapping("/{tradeRef}/events")` method returning `List<TradeEvent>`.
- The endpoint is protected with `@PreAuthorize("hasAnyRole('ADMIN','RECON_ANALYST')")`.
- The response is ordered by event timestamp ascending and includes every event for the trade — no pagination yet.
- An unauthenticated request returns 401; an authenticated user without one of the two roles returns 403.

<details>
<summary>Hint 1 — gentle direction</summary>

This is a thin controller — the work is in the query service it calls. Resist the urge to inline the JPA-to-DTO mapping in the controller. Keep the controller free of business logic: it should call one service method and return the result. Also think about who is allowed to see audit history: admins always, recon analysts because they need to debug breaks. Other roles must not see this data.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Create `backend/src/main/java/com/dbtraining/reconx/controller/AuditController.java`. Inject `AuditQueryService` (you may need to add a method `eventsForTrade(String tradeRef)` to the existing service that maps `AuditLogEntry` rows to `TradeEvent` DTOs). Apply `@PreAuthorize` at the class level so every method on the controller inherits it.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Class annotated `@RestController` + `@RequestMapping("/api/v1/audit/trades")` + `@PreAuthorize("hasAnyRole('ADMIN','RECON_ANALYST')")`. Constructor takes `AuditQueryService`. Single method: `@GetMapping("/{tradeRef}/events") public List<TradeEvent> getEvents(@PathVariable String tradeRef)`, returning `queryService.eventsForTrade(tradeRef)`. That is the entire controller — three lines plus annotations.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/main/java/com/dbtraining/reconx/controller/AuditController.java`.
2. Annotate `@RestController` + `@RequestMapping("/api/v1/audit/trades")` + `@PreAuthorize("hasAnyRole('ADMIN','RECON_ANALYST')")`.
3. Constructor-inject `AuditQueryService`.
4. Add `@GetMapping("/{tradeRef}/events") public List<TradeEvent> getEvents(@PathVariable String tradeRef)` returning `queryService.eventsForTrade(tradeRef)`.
5. Verify with an authenticated `ADMIN` token (200), an authenticated trader (403), and no token (401).

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

**▶ Run the project — verify TICKET-ADV138 end-to-end**

Read the audit log via the new endpoint.

```bash
curl -s http://localhost:8080/v1/audit | jq '.[0:5]'
curl -s http://localhost:8080/v1/audit/T-ADV132-D | jq
```

**Observe:**

- Response is a JSON array of audit rows ordered by `eventTimestamp` ascending.
- Each row shows `eventType`, `actor`, and a `before`/`after` snippet.
- Per-trade endpoint returns only rows for that `tradeRef`.
- Failure signal: 403 — the endpoint is missing an `@PreAuthorize` exemption for the admin role; check the Day 8 security config.

---

### Workshop 9 Part D — Metrics, Grafana, integration tests, AI review

The shortest block by minutes, the highest leverage by impact. By the end Grafana has a Kafka health row with three panels (consumer lag, throughput, DLQ count + alert), Testcontainers integration tests cover the happy and DLQ paths against a real broker, and Claude has reviewed your consumer config and you have filed a PR documenting each finding with an accept/reject/defer decision.

**Why this block matters:** the first three blocks make Kafka *work*; this block makes it *operable* and *defensible*. **Metrics + Grafana panels** mean a human can tell at a glance whether the pipeline is healthy — without that, "the consumer is lagging" is a tribal-knowledge fact only one engineer can diagnose. **DLQ count alert** is what wakes someone up before a customer notices. **Testcontainers integration tests** are the only way to catch the bugs that only appear when bytes actually cross a network — topic name typos, wrong partition counts, missing `groupId`, serialiser mismatches. A mocked Kafka client would happily pass all your tests and then fail in CI. **AI consumer-config review** is a sanity net: an extra reviewer who reads the YAML, spots the missing `max.poll.records` or the wrong `enable.auto.commit`, and writes you a PR comment. None of this code touches the data path — but without it, every Part-A-to-C piece you just built becomes opaque the moment you stop watching it.

---

### TICKET-ADV139 — Kafka metrics via Micrometer

**Goal:** Expose Kafka client metrics on the Prometheus actuator endpoint so Grafana can scrape them.

**What**
- `application.yml` with `management.metrics.binders.kafka.enabled: true`, `spring.kafka.consumer.properties.metric.reporters: io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics`, and `prometheus` added to `management.endpoints.web.exposure.include`.

**Why**
- Without this Prometheus wiring, the Grafana panels in ADV140 / ADV141 / ADV142 scrape an empty endpoint and Day 10's docker-compose dashboard demo lights up red -- the binder has to be enabled on both the Boot side and the Kafka client side or no metrics appear.

**Observe**
- `curl -s http://localhost:8080/actuator/prometheus | grep kafka_consumer` returns `kafka_consumer_records_lag`, `kafka_consumer_records_consumed_total`, and `kafka_consumer_fetch_total` with non-null samples after a handful of publishes.

**Done when:**
- `application.yml` enables `management.metrics.binders.kafka.enabled: true`.
- The consumer config sets `metric.reporters: io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics` under `spring.kafka.consumer.properties`.
- `management.endpoints.web.exposure.include` contains `prometheus`.
- `curl -s http://localhost:8080/actuator/prometheus | grep kafka_consumer` returns at least `kafka_consumer_records_lag`, `kafka_consumer_records_consumed_total`, and `kafka_consumer_fetch_total` with non-null values once you have published a few events.

<details>
<summary>Hint 1 — gentle direction</summary>

You do not need to write any Java for this. The Micrometer Kafka client metrics binder hooks into the underlying Kafka consumer and producer and exports their built-in metrics as Prometheus time series. The trap is that the binder has to be enabled in two places — the Spring Boot actuator side, and the Kafka client properties — and it is easy to set only one and wonder why no metrics appear.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Edit `backend/src/main/resources/application.yml`. Under `management.metrics.binders.kafka` set `enabled: true`. Under `management.endpoints.web.exposure.include` add `prometheus` (alongside `health`, `info`, `metrics`). Under `spring.kafka.consumer.properties` add `metric.reporters: io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics`. Also set `spring.json.trusted.packages: "com.dbtraining.reconx.dto"` for the JSON deserializer.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Three sections of `application.yml` to touch: `spring.kafka.consumer` (group-id, auto-offset-reset, deserializers, trusted packages, metric.reporters), `management.endpoints.web.exposure.include`, and `management.metrics.binders.kafka.enabled`. After restart, verify with `curl -s http://localhost:8080/actuator/prometheus | grep -E '^kafka_(consumer|producer)' | head -20`. You should see at least lag, records-consumed-total, and producer record-send-total.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `backend/src/main/resources/application.yml`.
2. Under `spring.kafka.consumer.properties` add `spring.json.trusted.packages: "com.dbtraining.reconx.dto"` and `metric.reporters: io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics`.
3. Under `management.endpoints.web.exposure.include` add `prometheus` alongside `health, info, metrics`.
4. Under `management.metrics.binders.kafka` set `enabled: true`; tag metrics with `application: ${spring.application.name}`.
5. Restart and run `curl -s http://localhost:8080/actuator/prometheus | grep kafka_consumer` — expect `kafka_consumer_records_lag`, `kafka_consumer_records_consumed_total`, `kafka_consumer_fetch_total`.

**Reference solution** (`backend/src/main/resources/application.yml` — Kafka + management sections):

```yaml
spring:
  # TICKET-ADV128 — Kafka producer/consumer defaults. Topics are created at startup
  # by KafkaTopicsConfig (see com.dbtraining.reconx.kafka.KafkaTopicsConfig).
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false
    consumer:
      group-id: reconx-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.dbtraining.reconx.dto
        spring.json.use.type.headers: false
        spring.json.value.default.type: com.dbtraining.reconx.dto.TradeEvent

# =============================================================================
# TICKET-ADV083-TICKET-ADV086 / TICKET-ADV097 — Actuator + Prometheus exposure
#  Custom metrics registered by com.dbtraining.reconx.observability.TradeMetrics
#  scrape via /actuator/prometheus.
# =============================================================================
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env,loggers
      base-path: /actuator
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
        reconciliation.duration: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
    tags:
      application: ${spring.application.name}
```

</details>

**▶ Run the project — verify TICKET-ADV139 end-to-end**

Boot the app and scrape the Prometheus endpoint for Kafka metrics.

```bash
./mvnw spring-boot:run
curl -s http://localhost:8080/actuator/prometheus | grep -E 'kafka_(consumer|producer)' | head -20
```

**Observe:**

- `kafka_consumer_records_consumed_total` present for each of the three groups.
- `kafka_producer_record_send_total` present for the producer client id.
- `kafka_consumer_records_lag` or `kafka_consumer_fetch_manager_records_lag` present per partition.
- All series carry `application="reconx"` tag.
- Failure signal: no `kafka_*` metrics returned — Micrometer Kafka binder is missing. Verify `micrometer-registry-prometheus` plus the Spring Kafka auto-config are on the classpath.

---

### TICKET-ADV140 — Grafana panel: consumer lag by topic

**Goal:** Add a Time Series panel to Grafana that shows consumer lag aggregated per topic, with red/yellow thresholds.

**What**
- A Grafana Time Series panel titled "Consumer lag by topic" in the Kafka dashboard row with query `sum by (topic) (kafka_consumer_records_lag)` and thresholds yellow at 100, red at 1000.

**Why**
- Lag-by-topic is the first signal operators check when Day 6's recon throughput falls behind, and the breakdown isolates which of `trade-events` / `recon-results` / `system-alerts` is the bottleneck before drilling into per-group metrics.

**Observe**
- Publishing 100 events while pausing the `audit-service` consumer for 30 s makes the panel's `trade-events` line climb above the yellow threshold visibly, then drop back to zero once the consumer resumes.

**Done when:**
- A new panel exists in your Kafka dashboard row with title "Consumer lag by topic".
- The query is `sum by (topic) (kafka_consumer_records_lag)`.
- Thresholds are configured: yellow at 100, red at 1000.
- After publishing 100 messages and pausing a consumer for 30 seconds, the panel visibly shows lag growth for the affected topic.

<details>
<summary>Hint 1 — gentle direction</summary>

Lag is the difference between the latest offset produced and the latest offset committed by a consumer group. A non-zero lag is not always bad — every consumer is briefly behind because polling is asynchronous. The question is whether lag is *growing*, which is what the time series view shows. A lag value of 0 is a feature, not a bug.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

The metric you want is `kafka_consumer_records_lag`. It has labels including `topic`, `partition`, and `client_id`. To get a per-topic view, aggregate with `sum by (topic) (...)`. Use a Time Series panel rather than a Stat panel — you care about the trend, not the instantaneous value. Set thresholds in the Field options pane, with absolute values 100 (yellow) and 1000 (red).

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

In Grafana: Add Panel → Time series. Query (PromQL): `sum by (topic) (kafka_consumer_records_lag)`. Legend: `{{topic}}`. Unit: short. Thresholds (Field tab): Mode = Absolute, steps at 0 green / 100 yellow / 1000 red. Save the panel into a Kafka row so it sits next to TICKET-ADV141 and TICKET-ADV142.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In Grafana, open the existing Kafka dashboard (or create one) and click Add Panel → Time series.
2. Set the title to "Consumer lag by topic" and the unit to `short`.
3. Enter the PromQL query and legend below.
4. Open the Field tab → Thresholds: Mode = Absolute, steps = 0 green, 100 yellow, 1000 red.
5. Save the panel in a "Kafka" row so it sits next to TICKET-ADV141 and TICKET-ADV142.
6. Pause a consumer for 30s after publishing 100 events and confirm visible lag growth.

**Reference solution** (PromQL + panel JSON snippet):

```promql
sum by (topic) (kafka_consumer_records_lag)
```

```json
{
  "title": "Consumer lag by topic",
  "type": "timeseries",
  "targets": [
    {
      "expr": "sum by (topic) (kafka_consumer_records_lag)",
      "legendFormat": "{{topic}}"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "color": "green",  "value": null },
          { "color": "yellow", "value": 100 },
          { "color": "red",    "value": 1000 }
        ]
      }
    }
  }
}
```

</details>

**▶ Run the project — verify TICKET-ADV140 end-to-end**

Open Grafana and confirm the consumer-lag panel renders.

```bash
docker compose up -d prometheus grafana
# Grafana: http://localhost:3000 (admin / admin)
```

**Observe:**

- "Kafka health" row contains a **Consumer Lag** time-series panel.
- Per-topic lag series visible (`trade-events`, `recon-results`, `system-alerts`) — typically 0 in a healthy state.
- Push 100 events with the consumer paused and watch lag climb visibly.
- Failure signal: "No data" — PromQL query likely refers to a metric name your binder doesn't emit. Cross-check against the ADV139 scrape output.

---

### TICKET-ADV141 — Grafana panel: messages/sec produced vs consumed

**Goal:** Add a Time Series panel that overlays produce rate and consume rate so you can spot a slow consumer at a glance.

**What**
- A Grafana Time Series panel titled "Throughput: produced vs consumed" overlaying `sum(rate(kafka_producer_record_send_total[1m]))` and `sum(rate(kafka_consumer_records_consumed_total[1m]))` as series labelled `produced` and `consumed`.

**Why**
- Watching rates surfaces *trend* (slow consumer, slow producer) faster than ADV140's lag panel, which only shows the *backlog* after divergence has already accumulated -- together they give early-warning and confirmation.

**Observe**
- Under steady-state publishing the two lines track within a few records/sec; deliberately throttling a consumer makes `produced` rise above `consumed` for as long as the throttle holds.

**Done when:**
- A new panel titled "Throughput: produced vs consumed" shows two series labelled `consumed` and `produced`.
- The `consumed` query is `sum(rate(kafka_consumer_records_consumed_total[1m]))`.
- The `produced` query is `sum(rate(kafka_producer_record_send_total[1m]))`.
- During a steady-state run the two lines should track each other closely; if you deliberately throttle a consumer they will diverge visibly.

<details>
<summary>Hint 1 — gentle direction</summary>

A healthy pipeline has produce rate roughly equal to consume rate over any meaningful window. If produce sits well above consume for more than a minute or two, lag is going to grow and the lag panel will confirm it. Watching the rates is often more useful than watching the lag because rates surface the *trend*, while lag surfaces the *backlog*.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

You want two PromQL queries on the same panel, both wrapped in `rate(...[1m])` and summed across labels. The metrics are `kafka_consumer_records_consumed_total` and `kafka_producer_record_send_total`. Use a `[1m]` window — short enough to be responsive, long enough to smooth out scrape jitter.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Time series panel. Two queries on the same panel: query A is `sum(rate(kafka_consumer_records_consumed_total[1m]))` with legend "consumed"; query B is `sum(rate(kafka_producer_record_send_total[1m]))` with legend "produced". Unit: msg/s. No thresholds — this panel is for trend reading. Place it next to the lag panel.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In Grafana, add a Time series panel titled "Throughput: produced vs consumed".
2. Add query A with the consumed PromQL and legend `consumed`.
3. Add query B with the produced PromQL and legend `produced`.
4. Set the unit to `msg/s` (Field → Standard options → Unit: short, append `msg/s` as a custom suffix if needed).
5. Place this panel in the same Kafka row, immediately to the right of the lag panel.
6. Throttle a consumer and confirm the two lines diverge visibly.

**Reference solution** (PromQL queries):

```promql
# Query A — consumed per second across all topics
sum(rate(kafka_consumer_records_consumed_total[1m]))
```

```promql
# Query B — produced per second across all topics
sum(rate(kafka_producer_record_send_total[1m]))
```

```json
{
  "title": "Throughput: produced vs consumed",
  "type": "timeseries",
  "targets": [
    { "expr": "sum(rate(kafka_consumer_records_consumed_total[1m]))", "legendFormat": "consumed" },
    { "expr": "sum(rate(kafka_producer_record_send_total[1m]))",      "legendFormat": "produced" }
  ],
  "fieldConfig": { "defaults": { "unit": "short" } }
}
```

</details>

**▶ Run the project — verify TICKET-ADV141 end-to-end**

Drive a steady event rate against the app and watch the produced/consumed series track each other.

```bash
# Light load loop (run for ~1 min):
for i in $(seq 1 60); do
  curl -s -X POST http://localhost:8080/api/v1/trades \
    -H 'Content-Type: application/json' \
    -d "{\"tradeRef\":\"T-LOAD-$i\",\"instrument\":\"USD/EUR\",\"notional\":1000,\"side\":\"BUY\"}" > /dev/null
  sleep 1
done
```

**Observe:**

- The Messages/sec panel shows two line series: `produced` and `consumed`, hovering around 1 msg/s.
- In steady state the lines overlap — produce rate matches consume rate.
- Failure signal: `consumed` line lags far behind `produced` — a consumer is slow or down. Cross-reference with the ADV140 lag panel.

---

### TICKET-ADV142 — Grafana panel: DLQ count and alert

**Goal:** Add a Stat panel showing total messages routed to the trade-events DLQ, plus an alerting rule that fires on any DLQ activity.

**What**
- A Grafana Stat panel titled "DLQ message count" with query `sum(kafka_consumer_records_consumed_total{topic="trade-events-dlq"})` plus a `KafkaDlqMessages` alerting rule (severity `critical`, fires when expression > 0 for one minute, annotation pointing operators to `/api/v1/admin/dlq`).

**Why**
- Any DLQ activity means ADV134's recoverer caught a real failure that ADV135's retries could not clear; this alert is what wakes someone up so ADV136's replay endpoint gets used before the backlog grows.

**Observe**
- Mocking the recon engine to throw makes the Stat panel tick to 1 within seconds and the `KafkaDlqMessages` alert transitions to `Firing` within ~90 s in Grafana's Alerting view.

**Done when:**
- A Stat panel titled "DLQ message count" shows `sum(kafka_consumer_records_consumed_total{topic="trade-events-dlq"})`.
- An alerting rule named `KafkaDlqMessages` triggers when that expression is greater than 0 for one minute, with severity `critical`.
- The alert annotation includes a summary and a description pointing operators to `/api/v1/admin/dlq`.
- Deliberately failing a consumer (mock the recon engine to throw) eventually fires the alert in Grafana within ~90 seconds.

<details>
<summary>Hint 1 — gentle direction</summary>

Why alert on consumed-total rather than on lag for the DLQ? Because you care that messages exist in the DLQ at all — not that the DLQ consumer is keeping up. If the DLQ consumer is fast, lag will sit at zero, but a message has still been DLQ'd and someone has to look at it. The right metric for "did anything go wrong" is the count, not the lag.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use a Stat panel for the count display. For the alert, use Grafana's unified alerting. The PromQL expression filters the consumed-total metric by `topic="trade-events-dlq"` and sums across labels. Alert evaluation interval `1m`, "for" duration `1m`, severity label `critical`, annotation summary `Messages in trade-events DLQ`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Stat panel. PromQL: `sum(kafka_consumer_records_consumed_total{topic="trade-events-dlq"})`. Alert rule (YAML or Grafana UI form): name `KafkaDlqMessages`, expr `... > 0`, `for: 1m`, labels `severity: critical`, annotations include `summary: Messages in trade-events DLQ` and a description telling the operator to inspect `/api/v1/admin/dlq`. Test by mocking `ReconciliationEngine` to throw and waiting for the alert to fire.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. In Grafana, add a Stat panel titled "DLQ message count".
2. Set the PromQL to the expression below.
3. Open Alerting → Alert rules → New rule; set name `KafkaDlqMessages`, expression `... > 0`, evaluation `1m`, `for: 1m`.
4. Add label `severity: critical` and annotation `summary: Messages in trade-events DLQ`, plus a description pointing operators to `/api/v1/admin/dlq`.
5. Save the panel in the Kafka row alongside the lag and throughput panels.
6. Mock `ReconciliationEngine` to throw and confirm the alert fires within ~90s.

**Reference solution** (PromQL + alert rule):

```promql
sum(kafka_consumer_records_consumed_total{topic="trade-events-dlq"})
```

```yaml
- alert: KafkaDlqMessages
  expr: sum(kafka_consumer_records_consumed_total{topic="trade-events-dlq"}) > 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Messages in trade-events DLQ"
    description: "At least one message has been DLQ'd in the last minute. Inspect /api/v1/admin/dlq."
```

</details>

**▶ Run the project — verify TICKET-ADV142 end-to-end**

Push a handful of malformed events to drive the DLQ panel and fire the alert.

```bash
# Produce 6 garbage messages via Kafdrop's Produce tab to trade-events
# Then check Prometheus + Grafana
open http://localhost:9090/alerts
open http://localhost:3000
```

**Observe:**

- "DLQ depth" panel in Grafana climbs from 0 to >0 within ~30s.
- Prometheus → Alerts page shows `TradeEventsDLQNonEmpty` (or your alert name) transition from `inactive` → `pending` → `firing`.
- Failure signal: panel stays at 0 — DLQ depth query is using `count` instead of `sum` over per-partition series; recheck the PromQL.

---

### TICKET-ADV143 — Integration test: end-to-end happy path

**Goal:** Write a `@SpringBootTest` that boots Testcontainers Kafka, publishes 100 events, and waits until 100 audit rows are persisted — using Awaitility instead of `Thread.sleep`.

**What**
- A `KafkaPipelineIT` `@SpringBootTest` + `@Testcontainers` class under `backend/src/test/java/com/dbtraining/reconx/kafka/` with a static `KafkaContainer` (`confluentinc/cp-kafka:7.6.0`), a `@DynamicPropertySource` for `spring.kafka.bootstrap-servers`, and an Awaitility assertion that publishes 100 `TradeEvent.created(...)` and waits for `auditRepo.count()` to increase by 100 within 30 s.

**Why**
- This locks in the producer-to-consumer-to-DB pipeline against regressions; Day 10's CI gate runs this test under `./mvnw verify`, and the `count-before / count-after delta` pattern protects the test from a non-empty seed database.

**Observe**
- `./mvnw -Dtest=KafkaPipelineIT verify` passes consistently locally and in CI without any `Thread.sleep`, with the Testcontainers Kafka log line showing the container started on a random host port.

**Done when:**
- A test class `KafkaPipelineIT` in `backend/src/test/java/com/dbtraining/reconx/kafka/` is annotated `@SpringBootTest` + `@Testcontainers`.
- A static `@Container KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))` is declared.
- A `@DynamicPropertySource` method registers `spring.kafka.bootstrap-servers` to `kafka::getBootstrapServers`.
- The test publishes 100 `TradeEvent.created(...)` events with distinct trade refs, then asserts via Awaitility that `auditRepo.count()` increases by 100 within 30 seconds. The test passes consistently with `./mvnw -Dtest=KafkaPipelineIT verify`.

<details>
<summary>Hint 1 — gentle direction</summary>

Two anti-patterns to dodge. First, `Thread.sleep(5000)` is forbidden — flaky on slow CI, wasteful on fast machines. Use Awaitility's polling. Second, do not assert on `auditRepo.count() == 100` directly — your test database may not be empty at the start. Capture the count before, then assert the delta after the 100 publishes.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Imports you need: `org.testcontainers.containers.KafkaContainer`, `org.testcontainers.utility.DockerImageName`, `org.testcontainers.junit.jupiter.{Container, Testcontainers}`, `org.awaitility.Awaitility`, `org.springframework.test.context.{DynamicPropertyRegistry, DynamicPropertySource}`. The `KafkaContainer` must be `static` so it is shared across tests in the class. The Docker image is `confluentinc/cp-kafka:7.6.0` (the trainer will have pre-pulled it at standup).

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Class annotations: `@SpringBootTest @Testcontainers`. Static `KafkaContainer` field with `@Container`. `@DynamicPropertySource` method `static void kafkaProps(DynamicPropertyRegistry r)` that calls `r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)`. Autowire `TradeEventProducer` and `AuditLogRepository`. Test method: capture `long before = auditRepo.count()`, loop 100 publishes via `IntStream.range(0,100)`, then `Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(...)` asserting count equals `before + 100`.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/test/java/com/dbtraining/reconx/kafka/KafkaPipelineIT.java` annotated `@SpringBootTest @Testcontainers`.
2. Declare a static `@Container KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))`.
3. Add a `@DynamicPropertySource` method registering `spring.kafka.bootstrap-servers` to `kafka::getBootstrapServers`.
4. Autowire `TradeEventProducer` and `AuditLogRepository`.
5. In the test: capture `long before = auditRepo.count()`, then `IntStream.range(0, 100).forEach(...)` to publish 100 distinct events.
6. Use `Awaitility.await().atMost(30s).pollInterval(500ms).untilAsserted(...)` asserting count equals `before + 100`.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/kafka/KafkaPipelineIT.java`):

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class KafkaPipelineIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired TradeEventProducer producer;
    @Autowired AuditLogRepository auditRepo;

    @Test
    void publishesAndConsumes100Events() {
        long before = auditRepo.count();

        IntStream.range(0, 100).forEach(i ->
                producer.publish(TradeEvent.created(
                        "TRD-IT-" + i,
                        JsonNodeFactory.instance.objectNode().put("price", i)
                ))
        );

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(auditRepo.count()).isEqualTo(before + 100)
                );
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV143 end-to-end**

Run the Testcontainers integration test against a real broker.

```bash
./mvnw -pl backend verify -Dtest=KafkaPipelineIT
```

**Observe:**

- Test log shows Testcontainers pulling/starting `confluentinc/cp-kafka:7.6.0`.
- Producer publishes 100 events; assertion confirms `audit_log_entries` grew by 100 within the Awaitility window.
- Build ends green: `Tests run: 1, Failures: 0, Errors: 0`.
- Failure signal: timeout on Awaitility — the consumer never woke up; verify the test's `spring.kafka.bootstrap-servers` is overridden to the Testcontainers broker address.

---

### TICKET-ADV144 — Integration test: DLQ on consumer failure

**Goal:** Write a Testcontainers test that proves a failing listener causes the message to land on `trade-events-dlq` after retries are exhausted.

**What**
- A `DlqRoutingIT` Testcontainers test that uses `@MockBean ReconciliationEngine` with `doThrow(new RuntimeException("boom"))`, publishes one `TradeEvent.created(...)` for `tradeRef = "TRD-DLQ-1"`, and asserts via Awaitility that a throwaway raw consumer (with `JsonDeserializer.TRUSTED_PACKAGES = "*"`) on `trade-events-dlq` sees that tradeRef within 30 s.

**Why**
- Proves the end-to-end failure path -- listener throws, ADV135 retries exhaust, ADV134 recoverer publishes to DLQ -- so any future regression in retry / DLQ wiring is caught before it hits production; pairs with ADV143 to cover happy and unhappy paths.

**Observe**
- `./mvnw -Dtest=DlqRoutingIT verify` passes and the test logs show three retry attempts on the in-process listener before the raw DLQ consumer picks up the `TRD-DLQ-1` record.

**Done when:**
- A test class `DlqRoutingIT` boots Testcontainers Kafka the same way `KafkaPipelineIT` does.
- The test uses `@MockBean ReconciliationEngine` and `Mockito.doThrow(new RuntimeException("boom")).when(reconEngine).scheduleRecon(anyString())` so every recon dispatch fails.
- The test publishes one `TradeEvent.created(...)` with `tradeRef = "TRD-DLQ-1"`, then asserts via Awaitility that a raw Kafka consumer subscribed to `trade-events-dlq` sees a record with that tradeRef within 30 seconds.
- The raw consumer is built with `JsonDeserializer.TRUSTED_PACKAGES = "*"` so it can deserialise the DLQ payload regardless of package.

<details>
<summary>Hint 1 — gentle direction</summary>

You are testing the path: producer → main topic → listener (throws) → retries (exhausted) → DLQ recoverer → DLQ topic. The naive way to assert is to query the DLQ from a separate test consumer, because the application's `DlqConsumer` may not have processed the message yet when you check. Build a throwaway raw consumer just for the assertion, with a unique groupId so it does not collide with the application's groups.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Use `@MockBean` to swap the real `ReconciliationEngine` for a Mockito mock, then `Mockito.doThrow(new RuntimeException("boom")).when(reconEngine).scheduleRecon(Mockito.anyString())`. For the raw assertion consumer: build `Properties` with `BOOTSTRAP_SERVERS_CONFIG = kafka.getBootstrapServers()`, a unique `GROUP_ID_CONFIG` (use `System.nanoTime()`), `AUTO_OFFSET_RESET_CONFIG = "earliest"`, key/value deserializers (StringDeserializer + JsonDeserializer), and `JsonDeserializer.TRUSTED_PACKAGES = "*"`.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

Helper method `boolean dlqHas(String tradeRef)` that builds a fresh `KafkaConsumer<String, TradeEvent>`, subscribes to `List.of("trade-events-dlq")`, polls for 5 seconds, and returns true if any record's `.value().tradeRef()` equals the supplied ref. Wrap in try-with-resources so the consumer closes. Test body: configure the mock, call `producer.publish(...)`, then `Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(dlqHas("TRD-DLQ-1")).isTrue())`. With three retries at 1s/2s/4s, expect the DLQ entry within roughly 8-10 seconds.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Create `backend/src/test/java/com/dbtraining/reconx/kafka/DlqRoutingIT.java` annotated `@SpringBootTest @Testcontainers`.
2. Reuse the static `KafkaContainer` + `@DynamicPropertySource` pattern from `KafkaPipelineIT`.
3. `@Autowired` `TradeEventProducer`; declare `@MockBean ReconciliationEngine reconEngine`.
4. In the test, `Mockito.doThrow(new RuntimeException("boom")).when(reconEngine).scheduleRecon(Mockito.anyString())`.
5. Publish `TradeEvent.created("TRD-DLQ-1", ...)` and await `dlqHas("TRD-DLQ-1")` true within 30s via Awaitility.
6. Implement `dlqHas(...)` as a raw `KafkaConsumer<String, TradeEvent>` (unique groupId, `JsonDeserializer.TRUSTED_PACKAGES = "*"`) inside try-with-resources.

**Reference solution** (`backend/src/test/java/com/dbtraining/reconx/kafka/DlqRoutingIT.java`):

```java
package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.service.ReconciliationEngine;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Properties;

import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DlqRoutingIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired TradeEventProducer producer;

    @MockBean ReconciliationEngine reconEngine;

    @Test
    void failingConsumerRoutesToDlq() {
        Mockito.doThrow(new RuntimeException("boom"))
                .when(reconEngine).scheduleRecon(Mockito.anyString());

        TradeEvent event = TradeEvent.created(
                "TRD-DLQ-1",
                JsonNodeFactory.instance.objectNode().put("price", 100)
        );
        producer.publish(event);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(dlqHas("TRD-DLQ-1")).isTrue()
                );
    }

    private boolean dlqHas(String tradeRef) {
        Properties p = new Properties();
        p.put(BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        p.put(GROUP_ID_CONFIG, "dlq-assert-" + System.nanoTime());
        p.put(AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        p.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, TradeEvent>(p)) {
            consumer.subscribe(java.util.List.of("trade-events-dlq"));
            var records = consumer.poll(Duration.ofSeconds(5));
            for (ConsumerRecord<String, TradeEvent> r : records) {
                if (tradeRef.equals(r.value().tradeRef())) return true;
            }
        }
        return false;
    }
}
```

</details>

**▶ Run the project — verify TICKET-ADV144 end-to-end**

Run the DLQ-routing integration test.

```bash
./mvnw -pl backend verify -Dtest=DlqRoutingIT
```

**Observe:**

- Testcontainers Kafka starts; the test publishes a poison event.
- The intentionally-failing consumer retries 3 times then quarantines the message.
- Assertion confirms exactly 1 message on `trade-events-dlq`; build green.
- Failure signal: DLQ assertion sees 0 — the `DefaultErrorHandler` wiring is not active under the test profile. Check the test imports `KafkaErrorHandlerConfig` (or that `@SpringBootTest` is wide enough).

---

### TICKET-ADV145 — AI-assisted Kafka consumer config review

**Goal:** Use Claude to review your Kafka consumer configuration for production readiness, then file a PR that records each finding with an explicit accept/reject/defer decision.

**What**
- A PR description listing every finding Claude returned on the Kafka consumer config review (covering backpressure / poll tuning, error handling and retry / DLQ, idempotence, observability, security) with config key, recommended value, one-line justification, plus a team decision column (accept / reject / defer) with at least one of each and a rationale per row.

**Why**
- This is the AI-policy checkpoint of Day 9: documented disagreement-or-agreement with the assistant beats blind copy-paste, and matches the ADR pattern used on Day 1 (ADV014) and the AI-review tickets on Day 6.

**Observe**
- `gh pr view` (or the GitHub UI) on the review PR shows the finding table with all three decision types present and a rationale beside each -- the YAML diff itself is smaller than the PR description.

**Done when:**
- You have pasted `application.yml` (the Kafka section) and `KafkaErrorHandlerConfig.java` into Claude with a focused review prompt covering: backpressure and poll tuning, error handling and retry/DLQ, idempotence, observability, and security.
- The review prompt explicitly tells Claude to list findings only (no whole-file rewrite), with config key, recommended value, and a one-line justification per finding.
- You have opened a PR whose description lists every finding Claude returned, with a column for your team's decision (accept / reject / defer) and a one-line rationale per decision.
- At least one finding is explicitly rejected, at least one accepted, and at least one deferred — with reasons stated.

<details>
<summary>Hint 1 — gentle direction</summary>

The AI-policy expectation here is that you never blindly accept and never blindly reject AI output. The deliverable is not "Claude's review" — it is *your decision* on each item Claude raised, defended in writing. If you accept every finding, you have abdicated. If you reject every finding, the review was pointless. The grader will read the PR description, not the YAML diff.

</details>

<details>
<summary>Hint 2 — concrete pointer</summary>

Structure the prompt around five named buckets so the output is gradable: (1) backpressure and poll tuning, (2) error handling and retry/DLQ, (3) idempotence and exactly-once semantics, (4) observability — metrics, logging, traces, (5) security — TLS, SASL, ACLs. Tell Claude the application context: trade reconciliation service, ~500 events/sec, strict audit requirements. Ask for: concrete config key, recommended value, one-line justification per finding. Forbid: whole-file rewrites.

</details>

<details>
<summary>Hint 3 — near-solution shape</summary>

PR description should be a table with columns: Area, Finding, Recommendation, Your decision (Accept/Reject/Defer), Rationale. Typical findings to expect from Claude include: `max.poll.records` default of 500 is risky for slow consumers, `ExponentialBackOff` has no jitter (known gap), producer `enable.idempotence` should be confirmed true, missing `spring.application.name` tag causes metric collisions, `bootstrap-servers` is PLAINTEXT (known dev gap, document for Day 10). Decide each one with a sentence; do not copy-paste Claude verbatim.

</details>

<details>
<summary>Hint 4 — step-by-step walkthrough with reference solution</summary>

**Steps:**

1. Open `application.yml` (Kafka section) and `KafkaErrorHandlerConfig.java`; paste both into a new Claude conversation.
2. Send the review prompt below verbatim (or adapted), forbidding whole-file rewrites and demanding concrete config key + recommended value + one-line justification per finding.
3. Collect Claude's findings into a table and create a new branch `feature/ticket-ADV145-kafka-config-review`.
4. For each finding, mark your team decision (Accept / Reject / Defer) with a one-line rationale; ensure at least one of each decision is present.
5. Apply any accepted findings to the YAML / Java; commit; open a PR titled "TICKET-ADV145 Kafka consumer config review".
6. Paste the prompt and the decision table into the PR description.

**Reference solution** (`docs/reviews/TICKET-ADV145-prompt.md` — the review prompt to send Claude):

```text
Review the following Spring Kafka consumer configuration for production
readiness. Flag any missing or risky settings in these areas:
  (1) backpressure & poll tuning,
  (2) error handling, retry & DLQ,
  (3) idempotence and exactly-once semantics,
  (4) observability — metrics, logging, traces,
  (5) security — TLS, SASL, ACLs.

For each finding, give the concrete config key, the recommended value, and a
one-line justification. Do NOT rewrite the whole file — just list findings.

Application context: trade reconciliation service, ~500 events/sec, strict
audit requirements.

=== application.yml (paste Kafka section here) ===
=== KafkaErrorHandlerConfig.java (paste here) ===
```

PR description table (paste into the PR body):

```
| # | Area          | Finding                                                  | Recommendation              | Decision | Rationale                                          |
|---|---------------|----------------------------------------------------------|-----------------------------|----------|----------------------------------------------------|
| 1 | Backpressure  | max.poll.records default 500 risks max.poll.interval.ms  | Set to 100                  | Accept   | Slow downstream — keeps poll loop responsive       |
| 2 | Error handling| ExponentialBackOff has no jitter                         | Add jitter (custom BackOff) | Defer    | Logged as backlog item; out of scope today         |
| 3 | Idempotence   | Producer enable.idempotence not asserted                 | enable.idempotence: true    | Accept   | Cheap insurance against duplicate sends            |
| 4 | Observability | Metrics tags missing spring.application.name             | Add tags.application        | Accept   | Prevents collisions across services in Prometheus  |
| 5 | Security      | bootstrap-servers is PLAINTEXT                           | Use SASL_SSL in prod        | Reject   | Known dev gap; tracked separately for Day 10       |
```

</details>

**▶ Run the project — verify TICKET-ADV145 end-to-end**

Paste your `KafkaConsumerConfig` (and `application.yml` Kafka block) into Claude with the prompt from `docs/reviews/TICKET-ADV145-prompt.md`, capture the findings, then file a PR.

```bash
# After applying accepted fixes locally:
git checkout -b ticket-ih145-kafka-config-review
git add backend/src/main/java/com/dbtraining/reconx/kafka/ backend/src/main/resources/application.yml docs/reviews/
git commit -m "TICKET-ADV145 Kafka consumer config review"
gh pr create --title "TICKET-ADV145 Kafka consumer config review" --body-file docs/reviews/TICKET-ADV145-decisions.md
```

**Observe:**

- Claude returns 3–5 distinct, actionable findings (not generic advice).
- The PR description includes a per-finding accept/reject/defer table with rationale.
- At least one of each decision type (accept, reject, defer) is recorded.
- Failure signal: all 5 findings are accepted with no rationale — re-run the review with a sharper "challenge each suggestion" follow-up prompt.

---

## End-of-day checklist

Before you close your laptop, walk through this list. If any item is amber, raise it in the debrief so the trainer can plan the Day-10 standup. Day 10 builds on every one of these.

- [ ] All four Kafka topics exist in Kafdrop with the right partition counts: `trade-events` (3), `trade-events-dlq` (3), `recon-results` (2), `system-alerts` (1). Topic names referenced as constants, not magic strings.
- [ ] `TradeEventProducer` publishes asynchronously, keyed by `tradeRef`, and is wired into the trade controller so a `POST /api/v1/trades` produces an event end-to-end.
- [ ] Three consumers (`ReconciliationConsumer`, `AuditEventConsumer`, `AlertConsumer`) registered in three distinct consumer groups (`recon-service`, `audit-service`, `alert-service`) — confirmed in Kafdrop's Consumers view.
- [ ] `DefaultErrorHandler` with `ExponentialBackOff(1000, 2.0)` and `maxElapsedTime = 8000` retries roughly three times and lands failures in `trade-events-dlq` on the same partition number; deserialization exceptions skip retries.
- [ ] `DlqConsumer` persists DLQ records to `dlq_messages`, and `POST /api/v1/admin/dlq/replay?eventId=...` re-publishes a single message back to the main topic. The endpoint is RBAC-protected for `ADMIN` only.
- [ ] `GET /api/v1/audit/trades/{tradeRef}/events` returns the event history oldest-first; `TradeAggregator.rebuild(ref)` reproduces the current state by folding events.
- [ ] Grafana has a Kafka row with three panels: consumer lag by topic (with red/yellow thresholds), produced vs consumed throughput, DLQ stat plus alert rule.
- [ ] Two Testcontainers integration tests are green locally: `KafkaPipelineIT` (publish 100, see 100 audit rows) and `DlqRoutingIT` (failing consumer routes to DLQ).
- [ ] A PR exists for TICKET-ADV145 with the Claude review prompt and a per-finding accept/reject/defer table — at least one of each decision recorded with rationale.
- [ ] You can answer the debrief questions out loud: why `tradeRef` is the message key (per-aggregate ordering), where you would look first when a DLQ message appears (logs → Kafdrop → DB → Grafana), and what extra piece you would need to rebuild a trade's state as of 30 minutes ago (a `rebuildAt(Instant)` overload plus an index on `occurred_at`).
