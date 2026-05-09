package com.github.swim_developer.framework.consumer.application.subscription.service;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractReconciliationServiceTest {

    record Desired(String id) {}
    record Current(String id) {}

    private List<Desired> created;
    private List<Current> deleted;
    private List<Current> currentSubscriptions;

    @BeforeEach
    void setUp() {
        created = new ArrayList<>();
        deleted = new ArrayList<>();
        currentSubscriptions = new ArrayList<>();
    }

    private AbstractReconciliationService<Desired, Current> service() {
        return new AbstractReconciliationService<>() {
            @Override
            protected boolean existsLocally(Desired desired) {
                return currentSubscriptions.stream().anyMatch(c -> c.id().equals(desired.id()));
            }
            @Override
            protected void createAndActivate(Desired desired) { created.add(desired); }
            @Override
            protected List<Current> loadCurrentSubscriptions() { return currentSubscriptions; }
            @Override
            protected boolean isStillDesired(Current current, List<Desired> desired) {
                return desired.stream().anyMatch(d -> d.id().equals(current.id()));
            }
            @Override
            protected void deactivateAndDelete(Current current) { deleted.add(current); }
            @Override
            protected String describeDesired(Desired desired) { return desired.id(); }
            @Override
            protected String describeCurrent(Current current) { return current.id(); }
        };
    }

    @Test
    void reconcileCreate_createsWhenNotExistsLocally() {
        service().reconcileCreate(List.of(new Desired("sub-1"), new Desired("sub-2")));
        assertThat(created).extracting(Desired::id).containsExactly("sub-1", "sub-2");
    }

    @Test
    void reconcileCreate_skipsAlreadyExistingLocally() {
        currentSubscriptions.add(new Current("sub-1"));
        service().reconcileCreate(List.of(new Desired("sub-1"), new Desired("sub-2")));
        assertThat(created).extracting(Desired::id).containsExactly("sub-2");
    }

    @Test
    void reconcileCreate_doesNothingWhenListEmpty() {
        service().reconcileCreate(List.of());
        assertThat(created).isEmpty();
    }

    @Test
    void reconcileCreate_doesNotThrow_whenCreateFails() {
        AbstractReconciliationService<Desired, Current> svc = new AbstractReconciliationService<>() {
            @Override protected boolean existsLocally(Desired d) { return false; }
            @Override protected void createAndActivate(Desired d) { throw new RuntimeException("fail"); }
            @Override protected List<Current> loadCurrentSubscriptions() { return List.of(); }
            @Override protected boolean isStillDesired(Current c, List<Desired> d) { return false; }
            @Override protected void deactivateAndDelete(Current c) { /* unused */ }
            @Override protected String describeDesired(Desired d) { return d.id(); }
            @Override protected String describeCurrent(Current c) { return c.id(); }
        };
        assertThatCode(() -> svc.reconcileCreate(List.of(new Desired("sub-fail")))).doesNotThrowAnyException();
    }

    @Test
    void reconcileDelete_deletesWhenNoLongerDesired() {
        currentSubscriptions.add(new Current("sub-old"));
        service().reconcileDelete(List.of(new Desired("sub-new")));
        assertThat(deleted).extracting(Current::id).containsExactly("sub-old");
    }

    @Test
    void reconcileDelete_keepsWhenStillDesired() {
        currentSubscriptions.add(new Current("sub-1"));
        service().reconcileDelete(List.of(new Desired("sub-1")));
        assertThat(deleted).isEmpty();
    }

    @Test
    void reconcileDelete_doesNothingWhenCurrentEmpty() {
        service().reconcileDelete(List.of(new Desired("sub-1")));
        assertThat(deleted).isEmpty();
    }

    @Test
    void reconcileDelete_doesNotThrow_whenDeleteFails() {
        currentSubscriptions.add(new Current("sub-fail"));
        AbstractReconciliationService<Desired, Current> svc = new AbstractReconciliationService<>() {
            @Override protected boolean existsLocally(Desired d) { return false; }
            @Override protected void createAndActivate(Desired d) { /* unused */ }
            @Override protected List<Current> loadCurrentSubscriptions() { return currentSubscriptions; }
            @Override protected boolean isStillDesired(Current c, List<Desired> d) { return false; }
            @Override protected void deactivateAndDelete(Current c) { throw new RuntimeException("fail"); }
            @Override protected String describeDesired(Desired d) { return d.id(); }
            @Override protected String describeCurrent(Current c) { return c.id(); }
        };
        assertThatCode(() -> svc.reconcileDelete(List.of())).doesNotThrowAnyException();
    }
}
