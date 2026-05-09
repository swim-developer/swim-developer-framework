package com.github.swim_developer.framework.consumer.infrastructure.in.health.mongo;

import com.github.swim_developer.framework.consumer.application.port.out.SwimPersistenceHealthPort;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractMongoDbHealthCheckTest {

    private AbstractMongoDbHealthCheck check(SwimPersistenceHealthPort port) {
        AbstractMongoDbHealthCheck c = new AbstractMongoDbHealthCheck(port);
        c.databaseName = "swim-test-db";
        return c;
    }

    @Test
    void call_returnsUpWithData_whenHealthy() {
        SwimPersistenceHealthPort port = mock(SwimPersistenceHealthPort.class);
        when(port.count()).thenReturn(15L);
        when(port.getCollectionName()).thenReturn("events");

        HealthCheckResponse response = check(port).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get()).containsKey("database");
        assertThat(response.getData().get()).containsKey("collection");
        assertThat(response.getData().get()).containsKey("documents");
    }

    @Test
    void call_returnsDown_whenExceptionThrown() {
        SwimPersistenceHealthPort port = mock(SwimPersistenceHealthPort.class);
        when(port.count()).thenThrow(new RuntimeException("DB unavailable"));
        when(port.getCollectionName()).thenReturn("events");

        HealthCheckResponse response = check(port).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getData().get()).containsKey("error");
    }
}
