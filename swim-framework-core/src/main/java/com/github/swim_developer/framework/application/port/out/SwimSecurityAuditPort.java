package com.github.swim_developer.framework.application.port.out;

public interface SwimSecurityAuditPort {

    void recordAuthenticationAttempt(String identity, boolean success, String reason);

    void recordAccessEvent(String identity, String resource, String action);

    void recordAuthorizationDecision(String identity, String resource, boolean granted);
}
