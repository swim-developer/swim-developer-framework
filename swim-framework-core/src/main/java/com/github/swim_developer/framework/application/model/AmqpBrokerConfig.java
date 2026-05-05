package com.github.swim_developer.framework.application.model;

import lombok.Builder;

@Builder
public record AmqpBrokerConfig(
    String host,
    int port,
    boolean sslEnabled,
    String username,
    String password,
    SaslMechanism saslMechanism,
    TlsConfig tls
) {
}
