package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.model.ProcessingContext;
import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionFilterPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractEventFilterService<E> implements SwimEventFilter<E> {

    protected final SwimSubscriptionFilterPort filterCache;
    protected final SwimDeadLetterPort deadLetterService;

    protected AbstractEventFilterService() {
        this.filterCache = null;
        this.deadLetterService = null;
    }

    protected AbstractEventFilterService(SwimSubscriptionFilterPort filterCache,
                                         SwimDeadLetterPort deadLetterService) {
        this.filterCache = filterCache;
        this.deadLetterService = deadLetterService;
    }

    protected abstract List<FilterRule<E>> buildFilterRules(E event);

    @Override
    public boolean passesSubscriptionFilter(String subscriptionId, E event) {
        for (FilterRule<E> rule : buildFilterRules(event)) {
            String value = rule.valueExtractor().apply(event);
            if (value != null && !filterCache.isAllowed(subscriptionId, rule.dimension(), value)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onFilterMismatch(ProcessingContext ctx, E event) {
        String failedDimensions = buildFilterRules(event).stream()
                .map(rule -> rule.dimension() + "=" + rule.valueExtractor().apply(event))
                .collect(Collectors.joining(", "));

        log.warn("SUBSCRIPTION_FILTER_MISMATCH: {}, Queue={}, MessageId={}",
                failedDimensions, ctx.queueName(), ctx.compositeMessageId());

        deadLetterService.sendToDeadLetterQueue(
                ctx.subscriptionId(), ctx.queueName(), ctx.amqpMessageId(),
                ctx.index(), ctx.xmlPayload(), "SUBSCRIPTION_FILTER_MISMATCH",
                new IllegalArgumentException(
                        "Event does not match subscription filters - " + failedDimensions));
    }
}
