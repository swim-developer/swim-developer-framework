package com.github.swim_developer.framework.consumer.application.messaging.processing;

import com.github.swim_developer.framework.application.port.out.SwimDeadLetterPort;
import com.github.swim_developer.framework.application.port.out.SwimIdempotencyPort;

public interface SwimEventProcessorConfig {

    String getServicePrefix();

    SwimIdempotencyPort getIdempotencyCache();

    SwimDeadLetterPort getDeadLetterService();
}
