package com.github.swim_developer.framework.unit.infrastructure.health;

import com.github.swim_developer.framework.application.port.out.AmqpPublisherHealthProvider;
import com.github.swim_developer.framework.infrastructure.in.health.AmqpHealthCheck;
import com.github.swim_developer.framework.infrastructure.in.health.DatabaseLivenessCheck;
import com.github.swim_developer.framework.infrastructure.in.health.DatabaseReadinessCheck;
import com.github.swim_developer.framework.infrastructure.in.health.LivenessCheck;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class HealthChecksTest {

    // ── LivenessCheck ──────────────────────────────────────────────────────────

    @Test
    void livenessCheck_alwaysReturnsUp() {
        var response = new LivenessCheck().call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).isEqualTo("Application liveness");
    }

    // ── AmqpHealthCheck ────────────────────────────────────────────────────────

    @Test
    void amqpHealthCheck_returnsUpWhenHostNotConfigured() {
        @SuppressWarnings("unchecked")
        Instance<AmqpPublisherHealthProvider> publisherHealth = mock(Instance.class);
        var check = new AmqpHealthCheck(publisherHealth, Optional.empty());

        var response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).contains("not configured");
    }

    @Test
    void amqpHealthCheck_returnsDownWhenNoPublisherAvailable() {
        @SuppressWarnings("unchecked")
        Instance<AmqpPublisherHealthProvider> publisherHealth = mock(Instance.class);
        when(publisherHealth.isUnsatisfied()).thenReturn(true);
        var check = new AmqpHealthCheck(publisherHealth, Optional.of("localhost:5671"));

        var response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

    @Test
    void amqpHealthCheck_returnsUpWhenPublisherIsHealthy() {
        AmqpPublisherHealthProvider provider = mock(AmqpPublisherHealthProvider.class);
        when(provider.isHealthy()).thenReturn(true);

        @SuppressWarnings("unchecked")
        Instance<AmqpPublisherHealthProvider> publisherHealth = mock(Instance.class);
        when(publisherHealth.isUnsatisfied()).thenReturn(false);
        when(publisherHealth.get()).thenReturn(provider);

        var check = new AmqpHealthCheck(publisherHealth, Optional.of("localhost:5671"));

        var response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void amqpHealthCheck_returnsDownWhenPublisherIsUnhealthy() {
        AmqpPublisherHealthProvider provider = mock(AmqpPublisherHealthProvider.class);
        when(provider.isHealthy()).thenReturn(false);

        @SuppressWarnings("unchecked")
        Instance<AmqpPublisherHealthProvider> publisherHealth = mock(Instance.class);
        when(publisherHealth.isUnsatisfied()).thenReturn(false);
        when(publisherHealth.get()).thenReturn(provider);

        var check = new AmqpHealthCheck(publisherHealth, Optional.of("localhost:5671"));

        var response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

    // ── DatabaseLivenessCheck ──────────────────────────────────────────────────

    @Test
    void dbLivenessCheck_returnsUpWhenDataSourceNotResolvable() {
        @SuppressWarnings("unchecked")
        Instance<AgroalDataSource> ds = mock(Instance.class);
        when(ds.isResolvable()).thenReturn(false);

        var response = new DatabaseLivenessCheck(ds).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).contains("not configured");
    }

    @Test
    void dbLivenessCheck_returnsUpWhenQuerySucceeds() throws Exception {
        Statement stmt = mock(Statement.class);
        Connection conn = mock(Connection.class);
        when(conn.createStatement()).thenReturn(stmt);

        AgroalDataSource ds = mock(AgroalDataSource.class);
        when(ds.getConnection()).thenReturn(conn);

        @SuppressWarnings("unchecked")
        Instance<AgroalDataSource> dsInstance = mock(Instance.class);
        when(dsInstance.isResolvable()).thenReturn(true);
        when(dsInstance.get()).thenReturn(ds);

        var response = new DatabaseLivenessCheck(dsInstance).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void dbLivenessCheck_returnsDownWhenQueryFails() throws Exception {
        AgroalDataSource ds = mock(AgroalDataSource.class);
        when(ds.getConnection()).thenThrow(new RuntimeException("DB unavailable"));

        @SuppressWarnings("unchecked")
        Instance<AgroalDataSource> dsInstance = mock(Instance.class);
        when(dsInstance.isResolvable()).thenReturn(true);
        when(dsInstance.get()).thenReturn(ds);

        var response = new DatabaseLivenessCheck(dsInstance).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

    // ── DatabaseReadinessCheck ─────────────────────────────────────────────────

    @Test
    void dbReadinessCheck_returnsUpWhenDataSourceNotResolvable() {
        @SuppressWarnings("unchecked")
        Instance<AgroalDataSource> ds = mock(Instance.class);
        when(ds.isResolvable()).thenReturn(false);

        var response = new DatabaseReadinessCheck(ds).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).contains("not configured");
    }

    @Test
    void dbReadinessCheck_returnsUpWhenQuerySucceeds() throws Exception {
        Statement stmt = mock(Statement.class);
        Connection conn = mock(Connection.class);
        when(conn.createStatement()).thenReturn(stmt);

        AgroalDataSource ds = mock(AgroalDataSource.class);
        when(ds.getConnection()).thenReturn(conn);

        @SuppressWarnings("unchecked")
        Instance<AgroalDataSource> dsInstance = mock(Instance.class);
        when(dsInstance.isResolvable()).thenReturn(true);
        when(dsInstance.get()).thenReturn(ds);

        var response = new DatabaseReadinessCheck(dsInstance).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void dbReadinessCheck_returnsDownWhenQueryFails() throws Exception {
        AgroalDataSource ds = mock(AgroalDataSource.class);
        when(ds.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        @SuppressWarnings("unchecked")
        Instance<AgroalDataSource> dsInstance = mock(Instance.class);
        when(dsInstance.isResolvable()).thenReturn(true);
        when(dsInstance.get()).thenReturn(ds);

        var response = new DatabaseReadinessCheck(dsInstance).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

}
