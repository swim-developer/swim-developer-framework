package com.github.swim_developer.framework.application.port.out;

/**
 * SPI for resolving the authenticated user identity and validating SWIM-specific
 * access roles within a request-scoped security context.
 *
 * <p>Implementations are responsible for extracting the current user from the
 * underlying authentication mechanism (e.g. JWT, mTLS certificate DN) and for
 * verifying that the user holds the required AMQP queue access role.</p>
 *
 * <p>The default implementation {@code JwtRoleValidator} resolves identity from
 * a MicroProfile JWT token and validates roles against Keycloak realm/resource_access claims.</p>
 */
public interface SwimSecurityContext {

    /**
     * Returns the authenticated username for the current request.
     *
     * @return username extracted from the security context; never {@code null}
     */
    String getUsername();

    /**
     * Validates that the given user holds the required AMQP queue access role.
     *
     * @param username the authenticated username to validate
     * @throws SecurityException if the user does not hold the required role
     */
    void validateAmqRole(String username);
}
