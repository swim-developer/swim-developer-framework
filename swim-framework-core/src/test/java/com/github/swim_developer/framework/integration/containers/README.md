# Integration Test Containers

Singleton Testcontainers structure for SWIM Framework integration tests.

## Architecture: Composition over Inheritance

All containers start **once** in `TestContainers` static block and are **reused** across all integration tests.

```
TestContainers (singleton holders)
    ↓
RedpandaTestContainer (wrapper + helpers)
MongoTestContainer (wrapper + helpers)
ArtemisTestContainer (wrapper + helpers)
```

## Available Containers

### Redpanda (Kafka-compatible)
- **Version**: v26.1.6
- **Wrapper**: `RedpandaTestContainer`
- **Helpers**: `getBootstrapServers()`, `deleteTopics()`, `cleanupAllTopics()`

### MongoDB
- **Version**: 8.0
- **Wrapper**: `MongoTestContainer`
- **Helpers**: `getConnectionString()`, `dropDatabase()`, `dropAllDatabases()`

### Apache Artemis (AMQP/JMS)
- **Version**: 2.44.0
- **Wrapper**: `ArtemisTestContainer`
- **Helpers**: `getAmqpUrl()`, `getHost()`, `getAmqpPort()`

## Usage Examples

### Single Container Test

```java
@QuarkusTest
@TestProfile(KafkaHealthCheckIT.Profile.class)
class KafkaHealthCheckIT {
    
    @Test
    void testKafkaHealth() {
        String bootstrapServers = RedpandaTestContainer.getBootstrapServers();
        // test logic
    }
    
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return RedpandaTestContainer.getQuarkusConfig();
        }
    }
}
```

### Multi-Container Test (Composition)

```java
@QuarkusTest
@TestProfile(FullStackIT.Profile.class)
class FullStackIT {
    
    @Test
    void testFullStack() {
        // Use all containers freely
        String kafka = RedpandaTestContainer.getBootstrapServers();
        String mongo = MongoTestContainer.getConnectionString();
        String artemis = ArtemisTestContainer.getAmqpUrl();
    }
    
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            var config = new HashMap<String, String>();
            config.putAll(RedpandaTestContainer.getQuarkusConfig());
            config.putAll(MongoTestContainer.getQuarkusConfig());
            config.putAll(ArtemisTestContainer.getQuarkusConfig());
            return config;
        }
    }
}
```

## Benefits

1. **Performance**: Containers start once, reused across all tests (~80% faster)
2. **Composition**: Use any combination without inheritance
3. **Single Source of Truth**: Version/config defined in one place
4. **Helpers**: Cleanup, setup, config utilities centralized
5. **Zero Duplication**: DRY principle enforced

## TLS / mTLS Test Infrastructure

Reusable components for testing mutual TLS authentication (SWIM-TIYP-0037, SPEC-170).

### TlsTestCertificateGenerator

Generates a complete PKI hierarchy in a temp directory:

- Self-signed CA (RSA 2048, SHA256, X.509 v3 with SKI/AKI extensions)
- Server certificate (CN=localhost, SAN=localhost)
- Valid client certificate
- Revoked client certificate
- CRL (Certificate Revocation List) containing the revoked certificate
- All artifacts written as PKCS12 keystores + truststore + CRL file

```java
TlsTestCertificateGenerator certs = TlsTestCertificateGenerator.generateAll();

certs.getBrokerKeystorePath();        // server keystore (PKCS12)
certs.getClientKeystorePath();        // valid client keystore
certs.getRevokedClientKeystorePath(); // revoked client keystore
certs.getTruststorePath();            // CA truststore
certs.getCrlPath();                   // CRL file
certs.getKeystorePassword();          // "changeit"
```

### ArtemisTlsContainer

Artemis broker with mTLS enabled (`needClientAuth=true`) via Testcontainers.
Generates a complete `broker.xml` dynamically and copies it into the container.

```java
TlsTestCertificateGenerator certs = TlsTestCertificateGenerator.generateAll();
ArtemisTlsContainer artemis = new ArtemisTlsContainer(certs);
artemis.start();

artemis.getAmqpsUrl();   // amqps://host:port
artemis.getAmqpsPort();  // mapped AMQPS port (5671)
artemis.getAmqpPort();   // mapped plain AMQP port (5672)
```

### TlsSecurityIT Coverage

| Test | Validates |
|---|---|
| `mtlsAmqpConnectionSucceeds` | AMQP mTLS send/receive works |
| `mtlsAmqpRejectsWithoutClientCert` | Missing client cert → rejected |
| `mtlsRestClientConnectsToHttpsServer` | REST mTLS works (Vert.x) |
| `crlRevocationRejectsRevokedServerCert` | Revoked cert → CRL rejects |
| `multipleCrlPathsSupported` | Multiple CRL paths configurable |
| `certificateReloadDetectsFileChange` | Cert rotation → reconnection |

### Using in Other Modules

Add the test-jar dependency to your module's `pom.xml`:

```xml
<dependency>
    <groupId>com.github.swim-developer</groupId>
    <artifactId>swim-framework-core</artifactId>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

Then use `TlsTestCertificateGenerator` and `ArtemisTlsContainer` directly in your tests.

## Adding New Containers

1. Add container to `TestContainers.java` static block
2. Create wrapper `XyzTestContainer.java` with helpers
3. Use composition in tests (no inheritance needed)
