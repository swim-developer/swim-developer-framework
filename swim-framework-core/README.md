# swim-framework-core

The foundation module. Defines the SPIs (Service Provider Interfaces) that every SWIM service implements, plus shared domain models, health checks, security, and messaging infrastructure.

This module has no opinion about which database, message broker, or leader election mechanism you use. It defines contracts, the adapters live in separate modules.

## SPIs

These are the interfaces your service implements. Each one represents a single concern.

### Event processing

| SPI | Purpose |
|-----|---------|
| `SwimEventExtractor<E, P>` | Extract domain events from a validated payload |
| `SwimPayloadValidator` | Validate incoming XML/JSON against schemas and business rules |
| `SwimOutboxRouter` | Route validated events to external systems (Kafka, AMQP) |
| `SwimIngressHandler` | Ingest events from external sources into the provider pipeline |
| `SwimMessageInterceptor` | Intercept messages at configurable points in the processing pipeline |

### Subscription and persistence

| SPI | Purpose |
|-----|---------|
| `SwimSubscription<E>` | Subscription contract with filter logic |
| `SwimSubscriptionRepository<E>` | Provider-side subscription persistence |
| `SwimConsumerSubscriptionRepository<D>` | Consumer-side subscription persistence |
| `SwimIdempotencyPort` | Duplicate detection |
| `FailedDeliveryStore<F>` | Failed delivery tracking with retry management |
| `SwimDeadLetterPort` | Dead letter queue operations |

### Infrastructure

| SPI | Purpose |
|-----|---------|
| `LeaderElectionStrategy` | Cluster leader election (Kubernetes, Infinispan, or Standalone) |
| `SwimAmqpPublisherPort` | AMQP message publishing |
| `QueueProvisioningStrategy` | Dynamic AMQP queue creation and security role management |
| `SwimSecurityContext` | User authentication and AMQP role validation |
| `SubscriptionHeartbeatPublisher` | Per-subscription heartbeat publishing |

## Domain models

Key records, enums, and interfaces shared across all modules:

- `SubscriptionStatus`, ACTIVE, PAUSED, TERMINATED, DELETED
- `QualityOfService`, AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE
- `ProcessingContext`, event processing metadata
- `PreparedEvent<E>`, validated event with deduplication hash
- `ProviderConfiguration`, provider connection details
- `ActiveSubscriptionInfo`, subscription metadata for heartbeat publishing

## Test infrastructure

The `test-jar` artifact includes reusable Testcontainers (Redpanda, MongoDB, Artemis) and a TLS certificate generator for mTLS integration testing. Other modules depend on it:

```xml
<dependency>
    <groupId>com.github.swim-developer</groupId>
    <artifactId>swim-framework-core</artifactId>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```
