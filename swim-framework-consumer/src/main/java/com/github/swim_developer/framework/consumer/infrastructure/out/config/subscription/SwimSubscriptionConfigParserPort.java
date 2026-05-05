package com.github.swim_developer.framework.consumer.infrastructure.out.config.subscription;

import java.util.List;

public interface SwimSubscriptionConfigParserPort {

    List<Object> parseDesiredSubscriptions();
}
