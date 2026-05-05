# swim-framework-consumer

Consumer-side infrastructure for SWIM services. Handles everything an ANSP Consumer needs to subscribe to external Providers, receive events via AMQP, and process them reliably.

## What it provides

### Subscription lifecycle

- `AbstractSubscriptionService<D, S>`, template for managing consumer subscriptions with reconciliation against remote providers
- `SubscriptionActivationExecutor`, activates subscriptions via REST calls to the Provider's Subscription Manager
- `SubscriptionReconciliationExecutor`, detects and resolves drift between local and remote subscription state
- `SubscriptionLossRecoveryExecutor`, re-subscribes when a subscription is lost on the provider side (404/410)
- `SubscriptionRenewalScheduler`, automatic renewal before expiry

### Event processing pipeline

Events flow through a pipeline: parse, extract, validate, filter, persist, route.

- `EventProcessingOrchestrator<E, P>`, orchestrates the full pipeline
- `AbstractEventFilterService<E>`, applies subscription-specific filters
- `AbstractEventPersistenceService<E>`, persists events to the consumer database
- `InboxBatchProcessor`, batches inbox messages for downstream production (Kafka)
- `OutboxDispatcher`, routes outbox events via `SwimOutboxRouter`

### AMQP connection management

- `AbstractAmqpConsumerManager`, manages AMQP consumer registration, pause, and unregister per subscription
- `AmqpConsumerLifecycleManager`, connection and message handler lifecycle
- `TlsCertificateReloader`, detects certificate file changes and triggers reconnection

### Heartbeat monitoring

- `SubscriptionHeartbeatChecker`, detects heartbeat timeouts per subscription
- `SubscriptionHeartbeatTracker`, tracks heartbeat sequence numbers
- `AbstractHeartbeatTimeoutHandler`, hook for custom timeout behavior (re-subscribe, alert)

### Duplicate detection

- `AbstractIdempotencyCache`, deduplication cache for incoming messages

## Multi-provider support

A single Consumer can maintain simultaneous AMQP connections to multiple external Providers. Each subscription has its own connection, heartbeat tracker, and filter configuration.
