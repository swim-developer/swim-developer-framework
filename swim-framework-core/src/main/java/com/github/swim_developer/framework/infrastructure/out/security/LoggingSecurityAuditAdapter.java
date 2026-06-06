package com.github.swim_developer.framework.infrastructure.out.security;

import com.github.swim_developer.framework.application.port.out.SwimSecurityAuditPort;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Audit adapter that emits structured log entries for all security-relevant events.
 *
 * <p>SWIM-TIYP-0114 requires that failed authentication attempts and access events
 * are recorded. Log aggregators (Loki, Elasticsearch) pick up these entries for
 * alerting and compliance reporting.</p>
 *
 * <p>Each log line uses key=value pairs so log aggregators can index fields without
 * additional parsing configuration.</p>
 */
@ApplicationScoped
@Slf4j
public class LoggingSecurityAuditAdapter implements SwimSecurityAuditPort {

    private static final String AUDIT_MARKER = "SWIM_SECURITY_AUDIT";

    @Override
    public void recordAuthenticationAttempt(String identity, boolean success, String reason) {
        if (success) {
            log.info("{} event=AUTHENTICATION_SUCCESS identity={}", AUDIT_MARKER, identity);
        } else {
            log.warn("{} event=AUTHENTICATION_FAILURE identity={} reason={}",
                    AUDIT_MARKER, identity, reason);
        }
    }

    @Override
    public void recordAccessEvent(String identity, String resource, String action) {
        log.info("{} event=ACCESS identity={} resource={} action={}",
                AUDIT_MARKER, identity, resource, action);
    }

    @Override
    public void recordAuthorizationDecision(String identity, String resource, boolean granted) {
        if (granted) {
            log.info("{} event=AUTHORIZATION_GRANTED identity={} resource={}",
                    AUDIT_MARKER, identity, resource);
        } else {
            log.warn("{} event=AUTHORIZATION_DENIED identity={} resource={}",
                    AUDIT_MARKER, identity, resource);
        }
    }
}
