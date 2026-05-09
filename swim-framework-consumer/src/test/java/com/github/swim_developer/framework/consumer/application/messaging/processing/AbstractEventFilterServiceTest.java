package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionFilterPort;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(TestNameLoggerExtension.class)
class AbstractEventFilterServiceTest {

    record StubEvent(String airport, String type) {}

    private SwimSubscriptionFilterPort filterCache;
    private SwimDeadLetterPort deadLetterService;

    @BeforeEach
    void setUp() {
        filterCache = mock(SwimSubscriptionFilterPort.class);
        deadLetterService = mock(SwimDeadLetterPort.class);
    }

    private AbstractEventFilterService<StubEvent> serviceWithRules() {
        return new AbstractEventFilterService<>(filterCache, deadLetterService) {
            @Override
            protected List<FilterRule<StubEvent>> buildFilterRules(StubEvent event) {
                return List.of(
                        new FilterRule<>("airport", StubEvent::airport),
                        new FilterRule<>("type", StubEvent::type)
                );
            }
        };
    }

    private ProcessingContext ctx() {
        return new ProcessingContext("sub-1", "queue-1", "msg-1", "<xml/>", 0, "inbox-1");
    }

    @Test
    void passesSubscriptionFilter_returnsTrueWhenAllRulesPass() {
        when(filterCache.isAllowed("sub-1", "airport", "EBBR")).thenReturn(true);
        when(filterCache.isAllowed("sub-1", "type", "DNOTAM")).thenReturn(true);

        boolean result = serviceWithRules().passesSubscriptionFilter("sub-1", new StubEvent("EBBR", "DNOTAM"));

        assertThat(result).isTrue();
    }

    @Test
    void passesSubscriptionFilter_returnsFalseWhenAnyRuleFails() {
        when(filterCache.isAllowed("sub-1", "airport", "EBBR")).thenReturn(true);
        when(filterCache.isAllowed("sub-1", "type", "DNOTAM")).thenReturn(false);

        boolean result = serviceWithRules().passesSubscriptionFilter("sub-1", new StubEvent("EBBR", "DNOTAM"));

        assertThat(result).isFalse();
    }

    @Test
    void passesSubscriptionFilter_skipsRuleWhenValueIsNull() {
        var svc = new AbstractEventFilterService<StubEvent>(filterCache, deadLetterService) {
            @Override
            protected List<FilterRule<StubEvent>> buildFilterRules(StubEvent event) {
                return List.of(new FilterRule<>("airport", e -> null));
            }
        };

        boolean result = svc.passesSubscriptionFilter("sub-1", new StubEvent(null, null));

        assertThat(result).isTrue();
    }

    @Test
    void onFilterMismatch_sendsToDeadLetterQueue() {
        var svc = serviceWithRules();

        svc.onFilterMismatch(ctx(), new StubEvent("EBBR", "DNOTAM"));

        verify(deadLetterService).sendToDeadLetterQueue(
                any(), any(), any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void onFilterMismatch_includesDimensionInfoInException() {
        var svc = serviceWithRules();

        svc.onFilterMismatch(ctx(), new StubEvent("EBBR", "DNOTAM"));

        verify(deadLetterService).sendToDeadLetterQueue(
                any(), any(), any(), any(Integer.class), any(), any(),
                org.mockito.ArgumentMatchers.argThat(ex ->
                        ex.getMessage() != null && ex.getMessage().contains("airport")));
    }
}
