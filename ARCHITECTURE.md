# swim-developer-framework — Architecture

> Diagrams use [Mermaid](https://mermaid.js.org) and render natively on GitHub.

swim-developer-framework is a shared Java library, not a deployable service. It provides the infrastructure skeleton that every SWIM Consumer and Provider needs, so that each service only implements what is unique to its domain.

---

## 1. System Context (C4 Level 1)

```mermaid
C4Context
    title System Context — swim-developer-framework

    System(framework, "swim-developer-framework", "Shared Java library: AMQP messaging, subscription lifecycle, heartbeat monitoring, idempotency, persistence abstractions")

    System_Ext(svcConsumer, "swim-*-consumer services", "DNOTAM Consumer, ED-254 Consumer — extend framework consumer modules")
    System_Ext(svcProvider, "swim-*-provider services", "DNOTAM Provider, ED-254 Provider — extend framework provider modules")
    System_Ext(broker, "AMQP Broker", "ActiveMQ Artemis — AMQP 1.0 / mTLS")
    System_Ext(sm, "Subscription Manager", "External REST API — subscription lifecycle")
    System_Ext(mongo, "MongoDB", "Event and subscription persistence for consumers")

    Rel(svcConsumer, framework, "extends consumer modules")
    Rel(svcProvider, framework, "extends provider modules")
    Rel(framework, broker, "manages AMQP connections", "AMQP 1.0 / mTLS")
    Rel(framework, sm, "manages subscription lifecycle", "REST / HTTPS / mTLS")
    Rel(framework, mongo, "base repository abstractions")
```

---

## 2. Container Diagram (C4 Level 2) — Module Structure

```mermaid
C4Container
    title Module Structure — swim-developer-framework

    System_Boundary(fw, "swim-developer-framework") {
        Container(core, "swim-framework-core", "Java library", "SPIs (ports), DTOs, cluster abstractions, security, health interfaces")
        Container(consumer, "swim-framework-consumer", "Java library", "AMQP consumer management, subscription lifecycle, heartbeat checker, inbox/outbox, reconciliation")
        Container(provider, "swim-framework-provider", "Java library", "AMQP publisher, subscription expiry, heartbeat generation, failed delivery recovery")
        Container(mongodb, "swim-framework-persistence-mongodb", "Java library", "MongoDB base repositories for consumer persistence")
    }

    Rel(consumer, core, "depends on")
    Rel(provider, core, "depends on")
    Rel(mongodb, core, "depends on")
```

---

## 3. Component Diagram (C4 Level 3) — swim-framework-core SPIs

```mermaid
C4Component
    title swim-framework-core — Extension Points (SPIs)

    Container_Boundary(core, "swim-framework-core") {
        Component(extractor, "SwimEventExtractor", "SPI / port/out", "Extracts typed event metadata from raw XML payload")
        Component(outboxRouter, "SwimOutboxRouter", "SPI / port/out", "Routes processed events to output destinations (fan-out via OutboxRouterFanOut)")
        Component(validator, "SwimPayloadValidator", "SPI / port/out", "Validates XML payload against XSD and business rules")
        Component(inboxStore, "SwimInboxStore", "SPI / port/out", "Persists received AMQP message to inbox store (default: Kafka)")
        Component(idempotency, "SwimIdempotencyPort", "SPI / port/out", "Deduplication — prevents duplicate event processing")
        Component(amqpPublisher, "SwimAmqpPublisherPort", "SPI / port/out", "Sends AMQP messages to subscriber queues")
        Component(leaderElection, "LeaderElectionStrategy", "SPI / port/out", "Leader election for scheduled tasks (Kubernetes or Infinispan)")
        Component(subRepo, "SwimSubscriptionRepository", "SPI / port/out", "Subscription persistence contract")
        Component(failedDelivery, "SwimFailedDeliveryStorePort", "SPI / port/out", "Persists failed delivery attempts for retry")
        Component(xmlUnmarshaller, "SwimXmlUnmarshallerPort", "SPI / port/out", "JAXB-based XML unmarshalling contract")
        Component(subFilter, "SwimSubscriptionFilterPort", "SPI / port/out", "Subscription-level event filtering")
        Component(securityAudit, "SwimSecurityAuditPort", "SPI / port/out", "Audit logging for security events")
    }
```

---

## 4. Component Diagram (C4 Level 3) — swim-framework-consumer Key Abstractions

```mermaid
C4Component
    title swim-framework-consumer — Key Abstract Components

    System_Ext(broker, "AMQP Broker")
    System_Ext(sm, "Subscription Manager REST API")

    Container_Boundary(fcons, "swim-framework-consumer") {
        Component(amqpMgr, "AbstractAmqpConsumerManager", "Abstract / SmallRye", "Manages AMQP connection lifecycle, reconnection, and consumer registration")
        Component(inboxConsumer, "AbstractInboxEventConsumer", "Abstract", "Reads from inbox store and dispatches to event processing orchestrator")
        Component(subService, "AbstractSubscriptionService", "Abstract", "Subscription create/pause/resume/delete with Subscription Manager integration")
        Component(reconciler, "AbstractReconciliationScheduler", "Abstract / Quartz", "Periodic reconciliation of local subscription state with Subscription Manager")
        Component(heartbeatChecker, "SubscriptionHeartbeatChecker", "CDI", "Monitors heartbeat messages; triggers timeout handler if heartbeat is missed")
        Component(heartbeatHandler, "AbstractHeartbeatTimeoutHandler", "Abstract", "Handles heartbeat timeout — default: resubscribe")
        Component(outboxScheduler, "ConsumerOutboxScheduler", "Quartz", "Polls inbox store and triggers outbox processing")
        Component(startupHandler, "AbstractSubscriptionStartupHandler", "Abstract", "Handles subscription state on application startup")
        Component(deadLetterService, "AbstractDeadLetterService", "Abstract", "Routes unprocessable events to dead letter store")
    }

    Rel(amqpMgr, broker, "connects to", "AMQP 1.0 / mTLS")
    Rel(subService, sm, "calls", "REST / HTTPS / mTLS")
    Rel(reconciler, sm, "syncs with", "REST / HTTPS / mTLS")
```

---

## 5. Component Diagram (C4 Level 3) — swim-framework-provider Key Abstractions

```mermaid
C4Component
    title swim-framework-provider — Key Abstract Components

    System_Ext(broker, "AMQP Broker")
    System_Ext(kafka, "Apache Kafka")

    Container_Boundary(fprov, "swim-framework-provider") {
        Component(subService, "AbstractProviderSubscriptionService", "Abstract", "Subscriber registration, queue provisioning, update, and deletion")
        Component(delivery, "AbstractEventDeliveryService", "Abstract", "Fan-out event delivery to all active subscriber AMQP queues with circuit breaker")
        Component(outboxProcessor, "AbstractOutboxEventProcessor", "Abstract", "Processes outbox events — validates, extracts, delivers")
        Component(outboxScheduler, "AbstractProviderOutboxScheduler", "Abstract / Quartz", "Polls outbox store and triggers event processing")
        Component(amqpPublisher, "AbstractAmqpPublisher", "Abstract / Qpid JMS", "Sends AMQP messages to individual subscriber queues")
        Component(failedRecovery, "AbstractFailedDeliveryRecoveryScheduler", "Abstract / Quartz", "Retries failed deliveries")
    }

    Rel(amqpPublisher, broker, "publishes to", "AMQP 1.0 / mTLS")
    Rel(outboxProcessor, kafka, "consumes from", "Apache Kafka")
```

---

## 6. Hexagonal Dependency Rule

All services built on swim-framework follow the same dependency rule. Dependencies always point inward — infrastructure depends on ports, never the reverse.

```mermaid
flowchart TD
    classDef infra fill:#DBEAFE,stroke:#2563EB,color:#1e3a5f
    classDef app fill:#DCFCE7,stroke:#16A34A,color:#14532d
    classDef domain fill:#FEF9C3,stroke:#CA8A04,color:#713f12

    subgraph INFRA["infrastructure (adapters)"]
        REST["REST Resources\nJAX-RS"]:::infra
        AMQP["Inbox Message Handler\nSmallRye Messaging"]:::infra
        PERSIST["Mongo / JPA Stores\nPanache"]:::infra
        HTTP_CLIENT["Subscription Manager Adapter\nMicroProfile REST Client"]:::infra
        OUTBOX["Outbox Message Handler\nKafka Producer"]:::infra
    end

    subgraph APP["application (use cases)"]
        SUB_UC["*SubscriptionUseCase"]:::app
        EVT_UC["*EventProcessingUseCase"]:::app
    end

    subgraph DOMAIN["domain (ports)"]
        PORT_IN["port/in\nManageSubscriptionPort"]:::domain
        PORT_OUT_S["port/out\nSubscriptionStore"]:::domain
        PORT_OUT_E["port/out\nEventStore"]:::domain
        PORT_OUT_SM["port/out\nRemoteSubscriptionManagerPort"]:::domain
    end

    REST -- "calls" --> PORT_IN
    SUB_UC -. "implements" .-> PORT_IN
    SUB_UC -- "uses" --> PORT_OUT_S
    SUB_UC -- "uses" --> PORT_OUT_SM
    EVT_UC -- "uses" --> PORT_OUT_E
    AMQP -- "delegates to" --> EVT_UC
    PERSIST -. "implements" .-> PORT_OUT_S
    PERSIST -. "implements" .-> PORT_OUT_E
    HTTP_CLIENT -. "implements" .-> PORT_OUT_SM
    EVT_UC -- "routes" --> OUTBOX
```
