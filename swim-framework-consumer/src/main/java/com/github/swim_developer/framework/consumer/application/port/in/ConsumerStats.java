package com.github.swim_developer.framework.consumer.application.port.in;

public record ConsumerStats(
        long totalEvents,
        long totalDlq,
        long activeSubscriptions,
        long totalSubscriptions
) {
}
