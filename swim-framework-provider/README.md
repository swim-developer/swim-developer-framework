# swim-framework-provider

Provider-side infrastructure for SWIM services. Handles everything an AISP Provider needs to manage subscriptions, publish events via AMQP, and maintain heartbeat contracts with subscribing Consumers.

## What it provides

### Subscription management

- `AbstractProviderSubscriptionService<E, R, S>`, template for CRUD operations with automatic queue provisioning and security role assignment
- `TopicService`, topic and queue management
- `TopicWildcardMatcher`, wildcard pattern matching for topic subscriptions
- `SubscriptionExpiryScheduler`, expires subscriptions past their `subscriptionEnd` time

### Event delivery

- `AbstractEventDeliveryService`, delivers events to all active subscriptions matching the event's topic
- `AbstractOutboxEventProcessor`, processes events from the outbox table
- `AbstractProviderOutboxScheduler`, recovers unprocessed outbox entries on a schedule
- `AfterCommitEventDispatcher`, dispatches events after the database transaction commits
- `AbstractFailedDeliveryRecoveryScheduler`, retries failed deliveries with backoff

### AMQP publishing

- `AbstractAmqpPublisher`, publishes messages to per-subscription AMQP queues with metadata headers
- `PerQueueCircuitBreaker`, per-queue circuit breaker that isolates failures to individual subscriptions

### Queue provisioning

- `SubscriptionQueueOrchestrator`, orchestrates queue and security role creation/deletion
- `KubernetesQueueProvisioner`, provisions queues via Kubernetes Secrets
- `AmqpQueueProvisioner`, provisions queues via Artemis Jolokia REST API

### Heartbeat

- `PerSubscriptionHeartbeatScheduler`, publishes JSON heartbeats to `{queue}.heartbeat` for each active subscription

### Security

- `JwtRoleValidator`, validates JWT-based AMQP roles for subscription access
