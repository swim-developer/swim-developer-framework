package com.github.swim_developer.framework.infrastructure.out.reflection;

import com.github.swim_developer.framework.domain.model.ActiveSubscriptionInfo;
import com.github.swim_developer.framework.application.model.AmqpBrokerConfig;
import com.github.swim_developer.framework.domain.model.DataValidationResult;
import com.github.swim_developer.framework.domain.model.DeliveryResult;
import com.github.swim_developer.framework.domain.model.ErrorDetail;
import com.github.swim_developer.framework.domain.model.FailedDeliveryInfo;
import com.github.swim_developer.framework.domain.model.HeartbeatTimeoutEvent;
import com.github.swim_developer.framework.application.model.ProviderConfiguration;
import com.github.swim_developer.framework.application.model.ResilienceConfig;
import com.github.swim_developer.framework.domain.model.SubscriptionExpiry;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import com.github.swim_developer.framework.application.model.SubscriptionManagerConfig;
import com.github.swim_developer.framework.domain.model.SubscriptionRenewalInfo;
import com.github.swim_developer.framework.application.model.SubscriptionStatusUpdate;
import com.github.swim_developer.framework.domain.model.SwimSubscriptionInfo;
import com.github.swim_developer.framework.application.model.SaslMechanism;
import com.github.swim_developer.framework.application.model.TlsConfig;
import com.github.swim_developer.framework.application.model.TlsKeystoreType;
import com.github.swim_developer.framework.domain.model.ValidationResult;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
    ActiveSubscriptionInfo.class,
    AmqpBrokerConfig.class,
    DataValidationResult.class,
    DeliveryResult.class,
    ErrorDetail.class,
    FailedDeliveryInfo.class,
    HeartbeatTimeoutEvent.class,
    ProviderConfiguration.class,
    ResilienceConfig.class,
    SubscriptionExpiry.class,
    SubscriptionHeartbeat.class,
    SubscriptionManagerConfig.class,
    SubscriptionRenewalInfo.class,
    SubscriptionStatusUpdate.class,
    SwimSubscriptionInfo.class,
    SaslMechanism.class,
    TlsConfig.class,
    TlsKeystoreType.class,
    ValidationResult.class
})
public class SwimFrameworkReflectionConfig {
}
