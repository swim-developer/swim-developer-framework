package com.github.swim_developer.framework.provider.application.subscription;

import com.github.swim_developer.framework.application.port.out.SwimSecurityContext;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionQueuePort;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionRepository;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.domain.model.SwimSubscriptionEntity;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@SuppressWarnings("unchecked")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractProviderSubscriptionServiceTest {

    private SwimSecurityContext security;
    private SwimSubscriptionQueuePort queuePort;
    private SwimSubscriptionRepository<TestEntity> repo;
    private StubService service;

    @BeforeEach
    void setUp() {
        security = mock(SwimSecurityContext.class);
        queuePort = mock(SwimSubscriptionQueuePort.class);
        repo = mock(SwimSubscriptionRepository.class);
        service = new StubService(security, queuePort, repo);
    }

    // ── resolveQueueName ───────────────────────────────────────────────────────

    @Test
    void resolveQueueName_returnsNullResolution_whenRequestedNameIsBlank() {
        var r = service.resolveQueueName("", "user1");
        assertThat(r.queueName()).isNull();
        assertThat(r.reuseExisting()).isFalse();
    }

    @Test
    void resolveQueueName_returnsNullResolution_whenRequestedNameIsNull() {
        var r = service.resolveQueueName(null, "user1");
        assertThat(r.queueName()).isNull();
    }

    @Test
    void resolveQueueName_reusesExistingQueue_whenOwnedByCurrentUser() {
        when(repo.findActiveOrPausedByQueueAndUser("DNOTAM.user1.abc", "user1"))
                .thenReturn(Optional.of(mock(TestEntity.class)));

        var r = service.resolveQueueName("DNOTAM.user1.abc", "user1");

        assertThat(r.queueName()).isEqualTo("DNOTAM.user1.abc");
        assertThat(r.reuseExisting()).isTrue();
    }

    @Test
    void resolveQueueName_returnsNull_whenQueueBelongsToAnotherUser() {
        when(repo.findActiveOrPausedByQueueAndUser(any(), any())).thenReturn(Optional.empty());
        when(repo.existsActiveOrPausedByQueue("DNOTAM.user2.abc")).thenReturn(true);

        var r = service.resolveQueueName("DNOTAM.user2.abc", "user1");

        assertThat(r.queueName()).isNull();
        assertThat(r.reuseExisting()).isFalse();
    }

    @Test
    void resolveQueueName_returnsRequestedName_whenFormatIsValid() {
        when(repo.findActiveOrPausedByQueueAndUser(any(), any())).thenReturn(Optional.empty());
        when(repo.existsActiveOrPausedByQueue(any())).thenReturn(false);

        var r = service.resolveQueueName("DNOTAM-user1-abc123", "user1");

        assertThat(r.queueName()).isEqualTo("DNOTAM-user1-abc123");
        assertThat(r.reuseExisting()).isFalse();
    }

    @Test
    void resolveQueueName_returnsNull_whenFormatIsInvalid() {
        when(repo.findActiveOrPausedByQueueAndUser(any(), any())).thenReturn(Optional.empty());
        when(repo.existsActiveOrPausedByQueue(any())).thenReturn(false);

        var r = service.resolveQueueName("WRONG-FORMAT", "user1");

        assertThat(r.queueName()).isNull();
    }

    // ── isValidQueueNameFormat ─────────────────────────────────────────────────

    @Test
    void isValidQueueNameFormat_valid_withPrefixAndUsername() {
        assertThat(service.isValidQueueNameFormat("DNOTAM-user1-abc123", "user1")).isTrue();
    }

    @Test
    void isValidQueueNameFormat_invalid_wrongPrefix() {
        assertThat(service.isValidQueueNameFormat("OTHER-user1-abc123", "user1")).isFalse();
    }

    @Test
    void isValidQueueNameFormat_invalid_fewerThanThreeParts() {
        assertThat(service.isValidQueueNameFormat("DNOTAM-user1", "user1")).isFalse();
    }

    @Test
    void isValidQueueNameFormat_invalid_doesNotContainUsername() {
        assertThat(service.isValidQueueNameFormat("DNOTAM-otheruser-abc", "user1")).isFalse();
    }

    // ── generateQueueName ──────────────────────────────────────────────────────

    @Test
    void generateQueueName_includesPrefixAndUserId() {
        String name = service.generateQueueName("user1");
        assertThat(name).startsWith("DNOTAM-").contains("user1");
    }

    // ── validateNotDeleted ─────────────────────────────────────────────────────

    @Test
    void validateNotDeleted_throws_whenDeleted() {
        TestEntity entity = entityWith(SubscriptionStatus.DELETED);
        assertThatThrownBy(() -> service.validateNotDeleted(entity))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DELETED");
    }

    @Test
    void validateNotDeleted_passes_forActiveStatus() {
        service.validateNotDeleted(entityWith(SubscriptionStatus.ACTIVE));
    }

    // ── findEntityByIdOrThrow ──────────────────────────────────────────────────

    @Test
    void findEntityByIdOrThrow_returnsEntity_whenFound() {
        UUID id = UUID.randomUUID();
        TestEntity entity = mock(TestEntity.class);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));

        assertThat(service.findEntityByIdOrThrow(id)).isEqualTo(entity);
    }

    @Test
    void findEntityByIdOrThrow_throws_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findEntityById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findEntityByIdOrThrow(id))
                .isInstanceOf(NotFoundException.class);
    }

    // ── ensureSubscriptionOwnedByCurrentUser ───────────────────────────────────

    @Test
    void ensureOwned_passes_whenOwnerMatchesCurrentUser() {
        when(security.getUsername()).thenReturn("user1");
        TestEntity entity = mock(TestEntity.class);
        when(entity.getUserId()).thenReturn("user1");

        assertThatNoException().isThrownBy(() -> service.ensureSubscriptionOwnedByCurrentUser(entity));
    }

    @Test
    void ensureOwned_throws_whenOwnerIsDifferentUser() {
        when(security.getUsername()).thenReturn("user2");
        TestEntity entity = mock(TestEntity.class);
        when(entity.getUserId()).thenReturn("user1");

        assertThatThrownBy(() -> service.ensureSubscriptionOwnedByCurrentUser(entity))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ensureOwned_throws_whenOwnerIsNull() {
        when(security.getUsername()).thenReturn("user1");
        TestEntity entity = mock(TestEntity.class);
        when(entity.getUserId()).thenReturn(null);

        assertThatThrownBy(() -> service.ensureSubscriptionOwnedByCurrentUser(entity))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_updatesAndPersists() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.PAUSED);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));
        when(security.getUsername()).thenReturn("user1");

        service.updateStatus(id, SubscriptionStatus.ACTIVE);

        verify(entity).setStatus(SubscriptionStatus.ACTIVE);
        verify(repo).persist(entity);
    }

    @Test
    void updateStatus_throws_whenSubscriptionIsDeleted() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.DELETED);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));
        when(security.getUsername()).thenReturn("user1");

        assertThatThrownBy(() -> service.updateStatus(id, SubscriptionStatus.ACTIVE))
                .isInstanceOf(BadRequestException.class);
    }

    // ── deleteSubscription ────────────────────────────────────────────────────

    @Test
    void deleteSubscription_setsDeletedStatusAndPersists() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.ACTIVE);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));
        when(security.getUsername()).thenReturn("user1");

        service.deleteSubscription(id);

        verify(entity).setStatus(SubscriptionStatus.DELETED);
        verify(repo).persist(entity);
    }

    // ── renewSubscription ─────────────────────────────────────────────────────

    @Test
    void renewSubscription_extendsSubscriptionEnd() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.ACTIVE);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));
        when(security.getUsername()).thenReturn("user1");

        service.renewSubscription(id, Duration.ofDays(7));

        verify(entity).setSubscriptionEnd(any(Instant.class));
        verify(repo).persist(entity);
    }

    @Test
    void renewSubscription_usesDefaultTtl_whenNullPassed() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.PAUSED);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));
        when(security.getUsername()).thenReturn("user1");

        service.renewSubscription(id, null);

        verify(entity).setSubscriptionEnd(any(Instant.class));
    }

    @Test
    void renewSubscription_throws_whenStatusIsTerminated() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.TERMINATED);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));
        when(security.getUsername()).thenReturn("user1");

        assertThatThrownBy(() -> service.renewSubscription(id, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot renew");
    }

    // ── terminateSubscription ─────────────────────────────────────────────────

    @Test
    void terminateSubscription_setsTerminatedStatus() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.ACTIVE);
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));

        service.terminateSubscription(id);

        verify(entity).setStatus(SubscriptionStatus.TERMINATED);
        verify(repo).persist(entity);
    }

    // ── purgeSubscription ─────────────────────────────────────────────────────

    @Test
    void purgeSubscription_deprovisionQueueAndDeletesEntity() {
        UUID id = UUID.randomUUID();
        TestEntity entity = entityForUser(id, "user1", SubscriptionStatus.DELETED);
        when(entity.getQueue()).thenReturn("DNOTAM.user1.abc");
        when(repo.findEntityById(id)).thenReturn(Optional.of(entity));

        service.purgeSubscription(id);

        verify(queuePort).deprovision("DNOTAM.user1.abc");
        verify(repo).delete(entity);
    }

    // ── listSubscriptions ─────────────────────────────────────────────────────

    @Test
    void listSubscriptions_returnsAllForCurrentUser() {
        when(security.getUsername()).thenReturn("user1");
        TestEntity e1 = entityForUser(UUID.randomUUID(), "user1", SubscriptionStatus.ACTIVE);
        TestEntity e2 = entityForUser(UUID.randomUUID(), "user1", SubscriptionStatus.PAUSED);
        when(repo.findByUserId("user1")).thenReturn(List.of(e1, e2));

        List<String> result = service.listSubscriptions();

        assertThat(result).hasSize(2);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private TestEntity entityWith(SubscriptionStatus status) {
        TestEntity e = mock(TestEntity.class);
        when(e.getStatus()).thenReturn(status);
        return e;
    }

    private TestEntity entityForUser(UUID id, String userId, SubscriptionStatus status) {
        TestEntity e = mock(TestEntity.class);
        when(e.getSubscriptionId()).thenReturn(id);
        when(e.getUserId()).thenReturn(userId);
        when(e.getStatus()).thenReturn(status);
        return e;
    }

    // ── Stub classes ──────────────────────────────────────────────────────────

    interface TestEntity extends SwimSubscriptionEntity {}

    static class StubService extends AbstractProviderSubscriptionService<TestEntity, String, String> {

        StubService(SwimSecurityContext sec, SwimSubscriptionQueuePort q,
                    SwimSubscriptionRepository<TestEntity> r) {
            super(sec, q, r);
        }

        @Override protected String getQueuePrefix() { return "DNOTAM-"; }
        @Override protected Duration getDefaultTtl() { return Duration.ofDays(30); }
        @Override protected String getRequestedQueueName(String req) { return req; }
        @Override protected String calculateHash(String req, String userId) { return userId + "-" + req; }
        @Override protected TestEntity createEntity(String req, String userId, String q, String hash) {
            return mock(TestEntity.class);
        }
        @Override protected String mapToResponse(TestEntity e) { return e.getSubscriptionId().toString(); }
        @Override protected void validateRequest(String req, String userId) { // intentional no-op for test stub
        }

        @Override public QueueResolution resolveQueueName(String r, String u) { return super.resolveQueueName(r, u); }
        @Override public boolean isValidQueueNameFormat(String q, String u) { return super.isValidQueueNameFormat(q, u); }
        @Override public String generateQueueName(String u) { return super.generateQueueName(u); }
        @Override public void validateNotDeleted(TestEntity e) { super.validateNotDeleted(e); }
        @Override public TestEntity findEntityByIdOrThrow(UUID id) { return super.findEntityByIdOrThrow(id); }
        @Override public void ensureSubscriptionOwnedByCurrentUser(TestEntity e) { super.ensureSubscriptionOwnedByCurrentUser(e); }
    }
}
