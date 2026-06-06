# AMQP Certificate-Based Login (SASL EXTERNAL)

## Context

During the development of the `swim-dnotam-provider-validator`, we discovered that ActiveMQ Artemis supports **certificate-based authentication** via **SASL EXTERNAL**. This mechanism authenticates the AMQP client using the TLS client certificate presented during the mTLS handshake, completely bypassing username/password credentials.

## How It Works

### SASL Mechanisms in AMQP

When an AMQP client connects to a broker, authentication happens via **SASL** (Simple Authentication and Security Layer). The broker advertises supported mechanisms, and the client picks one:

| Mechanism | Authentication Method | Credentials Required |
|---|---|---|
| **PLAIN** | Username + Password | Yes |
| **EXTERNAL** | TLS Client Certificate | No (certificate is the credential) |
| **ANONYMOUS** | No authentication | No |

### SASL EXTERNAL Flow

```
Client                                  Broker (Artemis)
  |                                        |
  |--- TLS Handshake (mTLS) ------------->|
  |    (client presents certificate)       |
  |<-- TLS Established -------------------|
  |                                        |
  |--- SASL EXTERNAL -------------------->|
  |    (no username/password sent)         |
  |                                        |
  |    Broker extracts Subject DN          |
  |    from client certificate             |
  |                                        |
  |    Broker maps DN to username          |
  |    via cert-users.properties           |
  |                                        |
  |    Broker assigns roles                |
  |    via cert-roles.properties           |
  |                                        |
  |<-- SASL OK (authenticated) -----------|
```

### Artemis JAAS Configuration

Certificate login uses a dedicated JAAS domain called `CertLogin` in `login.config`:

```
CertLogin {
   org.apache.activemq.artemis.spi.core.security.jaas.TextFileCertificateLoginModule required
       debug=false
       reload=true
       normalise=true
       org.apache.activemq.jaas.textfiledn.user="cert-users.properties"
       org.apache.activemq.jaas.textfiledn.role="cert-roles.properties";
};
```

### Certificate-to-User Mapping (`cert-users.properties`)

Maps client certificate Subject DN (via regex) to an Artemis username:

```properties
dnotam-subscriber=/.*O=mkcert development certificate.*/
```

Any certificate whose Subject DN contains `O=mkcert development certificate` will be authenticated as user `dnotam-subscriber`.

### User-to-Role Mapping (`cert-roles.properties`)

Assigns Artemis roles to certificate-authenticated users:

```properties
admin=dnotam-subscriber
```

The user `dnotam-subscriber` receives the `admin` role.

## Acceptor Configuration

### Enabling SASL EXTERNAL (default when `needClientAuth=true`)

When the AMQPS acceptor has `needClientAuth=true` and **does not** restrict `saslMechanisms`, Artemis automatically offers SASL EXTERNAL:

```xml
<acceptor name="amqps">
    tcp://0.0.0.0:5671?protocols=AMQP;sslEnabled=true;needClientAuth=true;...
</acceptor>
```

Most AMQP clients (including Vert.x AMQP Client and Qpid JMS) will **prefer SASL EXTERNAL over SASL PLAIN** when both are available and a client certificate has been presented.

### Forcing SASL PLAIN Only

To force username/password authentication even with mTLS, restrict the offered mechanisms:

```xml
<acceptor name="amqps">
    tcp://0.0.0.0:5671?protocols=AMQP;sslEnabled=true;needClientAuth=true;saslMechanisms=PLAIN;...
</acceptor>
```

The TLS client certificate is still validated (mTLS transport security), but **authentication** uses username/password via the `activemq` JAAS domain.

### Offering Both Mechanisms

To let the client choose:

```xml
<acceptor name="amqps">
    tcp://0.0.0.0:5671?protocols=AMQP;sslEnabled=true;needClientAuth=true;saslMechanisms=PLAIN,EXTERNAL;...
</acceptor>
```

> **Warning**: Most clients will prefer EXTERNAL when a certificate is available. To use PLAIN, the client must explicitly select it.

## Client-Side Configuration

### Vert.x AMQP Client — Forcing SASL PLAIN

The Vert.x AMQP Client does not expose a direct `saslMechanism` option. When both PLAIN and EXTERNAL are offered and a client certificate is present, it typically selects EXTERNAL.

To force PLAIN, the broker side must restrict to `saslMechanisms=PLAIN`.

### Qpid JMS Client — Selecting Mechanism

Qpid JMS allows explicit mechanism selection via the connection URI:

```
amqps://broker:5671?amqp.saslMechanisms=PLAIN
```

Or programmatically:

```java
JmsConnectionFactory factory = new JmsConnectionFactory();
factory.setRemoteURI("amqps://broker:5671?amqp.saslMechanisms=PLAIN");
factory.setUsername("marcelo");
factory.setPassword("password");
```

### Apache Camel AMQP Component

Via Qpid JMS connection string:

```properties
quarkus.qpid-jms.url=amqps://broker:5671?amqp.saslMechanisms=PLAIN&transport.trustStoreLocation=/certs/truststore.p12&transport.keyStoreLocation=/certs/keystore.p12
quarkus.qpid-jms.username=marcelo
quarkus.qpid-jms.password=password
```

## What We Discovered (Debugging History)

### Problem

The user `marcelo` was consistently denied `CONSUME` permission on queues provisioned for them:

```
AMQ119015: not authorized to create consumer,
AMQ229213: User: marcelo does not have permission='CONSUME'
  for queue DNOTAM.marcelo.f570e7db on address DNOTAM.marcelo.f570e7db
```

### Root Cause

1. The AMQPS acceptor on port 5671 had `needClientAuth=true` without restricting `saslMechanisms`.
2. Artemis offered both SASL PLAIN and SASL EXTERNAL.
3. The Vert.x AMQP client selected SASL EXTERNAL (certificate-based).
4. The client certificate (issued by `mkcert`) matched `cert-users.properties`, authenticating as `dnotam-subscriber`.
5. `dnotam-subscriber` had the global `admin` role, but **not** the queue-specific role `marcelo-swim-dnotam-v1-amq-role`.
6. The `DirectAccessGrantsLoginModule` (Keycloak) was never invoked — SASL EXTERNAL uses the `CertLogin` JAAS domain, not the `activemq` domain.

### Diagnosis Method

1. Enabled DEBUG logging for `org.keycloak` and `org.apache.activemq.artemis.spi.core.security.jaas` — **no logs appeared**, confirming Keycloak JAAS was not being invoked.
2. Queried Jolokia to list active sessions:
   ```bash
   curl -k -H "Origin: https://localhost" \
     'https://admin:admin@localhost:8161/console/jolokia/exec/org.apache.activemq.artemis:broker="amq-broker"/listSessionsAsJSON/""'
   ```
   Result: `"validatedUser": "dnotam-subscriber"` — not `marcelo`.
3. Checked `cert-users.properties`: confirmed `dnotam-subscriber` mapped to `mkcert` certificates.

### Fix Applied

Added `saslMechanisms=PLAIN` to the AMQPS acceptor in `broker.xml`, forcing password-based authentication via Keycloak.

## Use Cases for Certificate Login

### When to Use SASL EXTERNAL

- **Service-to-service communication**: No human user involved, identity is the service certificate.
- **High-security environments**: Certificate is a stronger credential than password.
- **Simplified credential management**: No passwords to rotate, certificate lifecycle managed by PKI.
- **SWIM Production**: EACP certificates identify services, not individual users.

### When to Use SASL PLAIN (with Keycloak)

- **User-specific authorization**: Queues/permissions tied to individual user identity.
- **Dynamic role assignment**: Roles managed in Keycloak, not in static properties files.
- **Multi-tenant scenarios**: Different users have different access levels on the same broker.
- **Development/testing**: Easier to test with username/password.

### Hybrid Approach (Future Consideration)

A production deployment could use **two separate acceptors**:

```xml
<!-- Service-to-service: certificate only -->
<acceptor name="amqps-services">
    tcp://0.0.0.0:5671?protocols=AMQP;sslEnabled=true;needClientAuth=true;saslMechanisms=EXTERNAL;...
</acceptor>

<!-- User connections: password via Keycloak -->
<acceptor name="amqps-users">
    tcp://0.0.0.0:5672?protocols=AMQP;sslEnabled=true;needClientAuth=true;saslMechanisms=PLAIN;...
</acceptor>
```

Each acceptor can reference a different `securityDomain` in Artemis for different JAAS chains.

## References

- [Artemis Security Documentation](https://activemq.apache.org/components/artemis/documentation/latest/security.html)
- [Artemis AMQP Acceptor Parameters](https://activemq.apache.org/components/artemis/documentation/latest/amqp.html)
- [JAAS TextFileCertificateLoginModule](https://activemq.apache.org/components/artemis/documentation/latest/security.html#certificate-based-authentication)
- [AMQP 1.0 SASL Specification](https://docs.oasis-open.org/amqp/core/v1.0/amqp-core-security-v1.0.html)
- [Vert.x AMQP Client](https://vertx.io/docs/vertx-amqp-client/java/)
- [Qpid JMS SASL Configuration](https://qpid.apache.org/releases/qpid-jms-1.11.0/docs/index.html)
