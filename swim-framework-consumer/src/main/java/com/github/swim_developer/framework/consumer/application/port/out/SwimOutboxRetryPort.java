package com.github.swim_developer.framework.consumer.application.port.out;

import com.github.swim_developer.framework.domain.model.SwimOutboxEvent;

public interface SwimOutboxRetryPort {

    void retryOutboxEvent(SwimOutboxEvent event);
}
