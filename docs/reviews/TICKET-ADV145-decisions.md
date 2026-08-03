# TICKET-ADV145 Kafka consumer config review

| # | Area | Finding | Recommendation | Decision | Rationale |
|---|---|---|---|---|---|
| 1 | Backpressure | `spring.kafka.consumer.properties.max.poll.records` is not constrained, so the default batch size can stretch processing and increase the chance of hitting `max.poll.interval.ms` under load. | Set `max.poll.records: 100` | Accept | Smaller batches keep the consumer loop responsive at ~500 events/sec and reduce rebalancing risk. |
| 2 | Error handling | `ExponentialBackOff` has no jitter, so retries can stay synchronized across consumers during an outage. | Add jitter via a custom backoff strategy | Defer | Useful hardening, but the current 1s/2s/4s retry path already covers the required DLQ flow and this is a follow-up improvement. |
| 3 | Idempotence | The producer config did not explicitly assert idempotent sends. | Set `spring.kafka.producer.properties.enable.idempotence: true` | Accept | Cheap protection against duplicate sends if retries occur during transient broker issues. |
| 4 | Observability | `management.metrics.tags.application` is already set and the Kafka metrics are already scraped, so there is no missing observability knob to add here. | No change | Reject | The suggested fix does not add value in this codebase because the application tag already exists and the Kafka metrics path is already working. |
| 5 | Security | `bootstrap-servers` defaults to `localhost:9092`, which is plaintext dev wiring rather than hardened transport. | Move prod/UAT to SASL_SSL or TLS-backed brokers | Reject | This repository intentionally keeps the local/dev default open; production transport security is an environment concern for deployment, not this config review. |

## Review prompt used

See [TICKET-ADV145-prompt.md](TICKET-ADV145-prompt.md).
