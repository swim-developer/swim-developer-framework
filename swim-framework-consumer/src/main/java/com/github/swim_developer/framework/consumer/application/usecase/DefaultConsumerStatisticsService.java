package com.github.swim_developer.framework.consumer.application.usecase;

import com.github.swim_developer.framework.consumer.application.port.in.ConsumerStats;
import com.github.swim_developer.framework.consumer.application.port.in.ConsumerStatisticsPort;
import com.github.swim_developer.framework.consumer.application.port.out.DeadLetterStore;
import com.github.swim_developer.framework.consumer.application.port.out.SwimEventCountPort;
import com.github.swim_developer.framework.consumer.application.port.out.SwimSubscriptionCountPort;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@DefaultBean
@ApplicationScoped
public class DefaultConsumerStatisticsService implements ConsumerStatisticsPort {

    private final Instance<SwimEventCountPort> eventCountInstance;
    private final DeadLetterStore deadLetterStore;
    private final Instance<SwimSubscriptionCountPort> subscriptionCountInstance;

    @Inject
    public DefaultConsumerStatisticsService(Instance<SwimEventCountPort> eventCountInstance,
                                            DeadLetterStore deadLetterStore,
                                            Instance<SwimSubscriptionCountPort> subscriptionCountInstance) {
        this.eventCountInstance = eventCountInstance;
        this.deadLetterStore = deadLetterStore;
        this.subscriptionCountInstance = subscriptionCountInstance;
    }

    @Override
    public ConsumerStats getStatistics() {
        long totalEvents = eventCountInstance.isResolvable()
                ? eventCountInstance.get().countEvents() : 0L;
        long totalDlq = deadLetterStore.countAll();
        SwimSubscriptionCountPort subCount = subscriptionCountInstance.isResolvable()
                ? subscriptionCountInstance.get() : null;
        long active = subCount != null ? subCount.countActiveSubscriptions() : 0L;
        long total = subCount != null ? subCount.countTotalSubscriptions() : 0L;

        return new ConsumerStats(totalEvents, totalDlq, active, total);
    }
}
