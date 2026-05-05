# swim-framework-persistence-mongodb

MongoDB adapter for consumer-side persistence. Implements the subscription repository and dead letter store contracts defined in `swim-framework-core` and `swim-framework-consumer`.

## What it provides

- `AbstractMongoSubscriptionRepository<D>`, generic MongoDB repository for consumer subscriptions, with Caffeine caching and queue-name indexing
- `MongoDeadLetterStore`, dead letter persistence for messages that fail processing
- `AbstractMongoIndexInitializer`, creates MongoDB indexes on application startup

## Usage

Extend `AbstractMongoSubscriptionRepository` with your subscription document type:

```java
@ApplicationScoped
public class DnotamSubscriptionRepository 
        extends AbstractMongoSubscriptionRepository<DnotamSubscriptionDocument> {
}
```

The base class provides `findBySubscriptionId()`, `findActiveSubscriptions()`, `findByQueueName()`, `findByConfigHash()`, `updateStatus()`, and cache invalidation.

## Dependencies

- `swim-framework-core`
- `swim-framework-consumer`
- Quarkus MongoDB Panache
