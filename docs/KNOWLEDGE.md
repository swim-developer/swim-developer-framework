# swim-developer-framework — Knowledge Base


## What This Is

Reusable infrastructure framework for all SWIM services. A new service implementing only domain logic (~700-900 lines) instead of duplicating 5700+ lines of infrastructure. Extracted from the DNOTAM implementation after ~55% of the code proved domain-agnostic. Proven by a second service: ED-254.

**6 Maven modules** — `swim-framework-core`, `swim-framework-consumer`, `swim-framework-provider`, `swim-framework-persistence-mongodb`, `swim-framework-leader-kubernetes`, `swim-framework-leader-infinispan`.

## SPIs (Contracts Every Service Implements)

| SPI | Pattern | Purpose |
|-----|---------|---------|
| `SwimEventExtractor<T>` | Adapter | Extract metadata from raw payload |
| `SwimInboxStore` | Strategy | Persist received AMQP message to inbox (EP1) |
| `SwimInboxReader` | Template Method | Read inbox and process batches (EP2) |
| `SwimOutboxRouter` | Strategy | Route validated event to destination (EP3, fan-out) |
| `SwimPayloadValidator` | Strategy | XSD + business rules validation |
| `SwimMessageInterceptor` | Chain of Responsibility | Optional post-validation chain |
| `SwimIngressHandler` | Strategy | Push external events into provider pipeline |
| `SubscriptionHeartbeatPublisher` | Strategy | Provider per-subscription heartbeat |
| `ActiveSubscriptionSupplier` | Strategy | Provider active subscription enumeration |
| `SubscriptionExpiryStrategy` | Strategy | Provider subscription expiry logic |
| `SubscriptionRenewalStrategy` | Strategy | Consumer auto-renewal + 404/410 recovery |

## Consumer Framework Key Classes

| Package | Key Classes |
|---------|-------------|
| `consumer.subscription/` | `AbstractAmqpConsumerManager`, `AmqpConnectionRegistry`, `SmClientRegistry`, `SwimSubscriptionRenewalScheduler` |
| `consumer.heartbeat/` | `SubscriptionHeartbeatTracker`, `SubscriptionHeartbeatChecker`, `HeartbeatTimeoutEvent` |
| `consumer.messaging/` | `AbstractInboxEventConsumer`, `AbstractEventProcessor`, `AbstractDeadLetterService`, `AbstractIdempotencyCache`, `OutboxDispatcher`, `OutboxRouterFanOut` |
| `consumer.health/` | `AbstractConsumerAmqpHealthCheck`, `AbstractMongoDbHealthCheck`, `ConsumerReconciliationHealthCheck` |

## Provider Framework Key Classes

| Package | Key Classes |
|---------|-------------|
| `provider.subscription/` | `AbstractEventDeliveryService`, `AbstractProviderSubscriptionService`, `SwimSubscriptionExpiryScheduler` |
| `provider.heartbeat/` | `PerSubscriptionHeartbeatScheduler` — sends JSON heartbeat to `{queue}.heartbeat` |
| `provider.amqp/` | `AbstractAmqpPublisher` |
| `provider.messaging/` | `AbstractOutboxEventProcessor` |
| `provider.queue/` | `ArtemisJmxQueueProvisioner` (dev), `KubernetesQueueProvisioner` (prod) |

## Extension Points (Consumer Pipeline)

| EP | SPI | Default Module | How to swap |
|----|-----|----------------|-------------|
| EP1 | `SwimInboxStore` | `swim-inbox-store-kafka` (`KafkaInboxStore` @Default) | Replace Maven dependency |
| EP2 | `SwimInboxReader` | `swim-inbox-reader-kafka` (extend + add `@Incoming`) | Replace Maven dependency |
| EP3 | `SwimOutboxRouter` | `swim-outbox-kafka-dnotam` / `swim-outbox-kafka-ed254` | Replace Maven dependency |

Multiple EP3 implementations coexist — `OutboxRouterFanOut` dispatches to ALL in parallel, independent timeouts (default 10s). Failure in one router does not affect others.

## Inbox/Outbox Pattern

```
1. AMQ Broker → AbstractAmqpConsumerManager (Inbox)
   → persist to Kafka inbox topic (EP1: KafkaInboxStore)
   → ACK only after Kafka confirms

2. Kafka inbox topic → AbstractKafkaInboxReader (EP2)
   → XSD validation → idempotency (SHA-256, Caffeine L1 + MongoDB L2)
   → persist domain event → OutboxDispatcher

3. OutboxDispatcher → OutboxRouterFanOut (EP3)
   → fan-out to all SwimOutboxRouter implementations (parallel, per-router timeout)
```

**Rule**: Accept first, validate after. Zero validation before ACK to broker.

## Heartbeat & Self-Healing

- Provider: `PerSubscriptionHeartbeatScheduler` sends heartbeat to `{queue}.heartbeat` for every ACTIVE/PAUSED subscription.
- Consumer: `SubscriptionHeartbeatChecker` detects timeout `(now - lastHeartbeat) > 45s`. On timeout → `GET /subscriptions/{id}` → if 404/410 → `reconcileCreate` (delete local, re-subscribe).

## Resilience

| Pattern | Implementation |
|---------|----------------|
| Persistence before ACK | Zero-loss inbox |
| Circuit breaker | Per-provider, 5 failures → OPEN 30s → HALF-OPEN probe |
| Exponential backoff | SM retry: `baseDelay × 2^(attempt-1)`, max 30s |
| Idempotency | SHA-256, Caffeine L1 + MongoDB L2 |
| DLQ | `AbstractDeadLetterService` + Kafka DLQ topic |
| Leader election | Kubernetes Lease API (prod) or Infinispan (alt) |

## Build

```bash
mvn clean install -DskipTests          # install to local Maven repo (required before building services)
mvn clean test                          # unit tests
mvn verify -DskipITs=false             # unit + integration tests (Testcontainers: MongoDB, Artemis, Redpanda)
```

**NEVER modify this framework without explicit user authorization.** Any change risks destabilizing all dependent services.

## ADRs

| ADR | Decision |
|-----|----------|
| 006 | 3 framework modules (core/consumer/provider) — eliminates split-package CDI warnings |
| 007 | SPI-first design — interfaces define contracts, services implement only what differs |
| 009 | Per-subscription heartbeat — JSON to `{queue}.heartbeat`, consumer self-healing |
| 014 | Constructor injection for testability — no reflection |
| 015 | Multi-provider — consumer connects to N providers simultaneously |
| 016 | Programmatic resilience for SM REST clients — per-provider timeouts |
| 017 | Circuit breaker per provider |
| 018 | Exponential backoff — SM retry and inbox recovery |
