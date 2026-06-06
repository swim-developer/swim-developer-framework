package com.github.swim_developer.framework.consumer.infrastructure.in.rest;

import com.github.swim_developer.framework.consumer.application.port.in.ConsumerStats;
import com.github.swim_developer.framework.consumer.application.port.in.ConsumerStatisticsPort;
import com.github.swim_developer.framework.consumer.application.port.out.DeadLetterStore;
import com.github.swim_developer.framework.consumer.infrastructure.out.dlq.DeadLetterMessage;
import com.github.swim_developer.framework.infrastructure.in.rest.PageResponse;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractOperationalResourceTest {

    private DeadLetterStore dlqRepository;
    private ConsumerStatisticsPort statisticsPort;
    private AbstractOperationalResource resource;

    @BeforeEach
    void setUp() {
        dlqRepository = mock(DeadLetterStore.class);
        statisticsPort = mock(ConsumerStatisticsPort.class);
        resource = new AbstractOperationalResource(dlqRepository, statisticsPort) {};
    }

    @Test
    void listDlqMessages_returnsPaginatedResponse() {
        DeadLetterMessage msg = dlqMessage("id-1", "amqp-1");
        when(dlqRepository.findAllPaginated(0, 20)).thenReturn(List.of(msg));
        when(dlqRepository.countAll()).thenReturn(1L);

        Response response = resource.listDlqMessages(0, 20);

        assertThat(response.getStatus()).isEqualTo(200);
        PageResponse<?> page = (PageResponse<?>) response.getEntity();
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void listDlqMessages_returnsEmptyPage() {
        when(dlqRepository.findAllPaginated(0, 20)).thenReturn(List.of());
        when(dlqRepository.countAll()).thenReturn(0L);

        Response response = resource.listDlqMessages(0, 20);

        assertThat(response.getStatus()).isEqualTo(200);
        PageResponse<?> page = (PageResponse<?>) response.getEntity();
        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void countDlqMessages_returnsCount() {
        when(dlqRepository.countAll()).thenReturn(42L);

        Response response = resource.countDlqMessages();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity().toString()).contains("42");
    }

    @Test
    void getStats_returnsStatistics() {
        ConsumerStats stats = new ConsumerStats(100L, 5L, 3L, 10L);
        when(statisticsPort.getStatistics()).thenReturn(stats);

        Response response = resource.getStats();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(stats);
    }

    private DeadLetterMessage dlqMessage(String id, String amqpId) {
        DeadLetterMessage m = new DeadLetterMessage();
        m.setId(id);
        m.setAmqpMessageId(amqpId);
        m.setMessageIndex(0);
        m.setSubscriptionId("sub-1");
        m.setQueueName("queue-1");
        m.setErrorType("ERROR");
        m.setErrorMessage("msg");
        m.setRawPayload("<xml/>");
        m.setReceivedAt(Instant.now());
        m.setFailedAt(Instant.now());
        return m;
    }
}
