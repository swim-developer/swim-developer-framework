package com.github.swim_developer.framework.provider.infrastructure.out.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import com.github.swim_developer.framework.application.port.out.SwimSecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;

@ApplicationScoped
@Slf4j
public class JwtRoleValidator implements SwimSecurityContext {

    private final JsonWebToken jwt;
    private final String amqRoleSuffix;

    @Inject
    public JwtRoleValidator(
            JsonWebToken jwt,
            @ConfigProperty(name = "swim.amq.role.suffix") String amqRoleSuffix) {
        this.jwt = jwt;
        this.amqRoleSuffix = amqRoleSuffix;
    }

    public String getUsername() {
        String username = jwt.getName();
        if (username == null || username.isBlank()) {
            username = jwt.getClaim("preferred_username");
        }
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        return username;
    }

    public String getExpectedAmqRole(String username) {
        return username + amqRoleSuffix;
    }

    public boolean hasAmqRole(String username) {
        String expectedRole = getExpectedAmqRole(username);
        return hasRoleInResourceAccess(expectedRole) || hasRoleInRealmAccess(expectedRole);
    }

    public void validateAmqRole(String username) {
        if (!hasAmqRole(username)) {
            String expectedRole = getExpectedAmqRole(username);
            throw new SecurityException(
                    String.format("User '%s' does not have required role '%s' for AMQP queue access",
                            username, expectedRole));
        }
    }

    private boolean hasRoleInResourceAccess(String expectedRole) {
        JsonObject resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null) {
            return false;
        }
        for (Map.Entry<String, JsonValue> entry : resourceAccess.entrySet()) {
            if (entry.getValue() instanceof JsonObject clientObj) {
                JsonArray rolesArray = clientObj.getJsonArray("roles");
                if (rolesArray != null && containsRole(rolesArray, expectedRole)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRoleInRealmAccess(String expectedRole) {
        JsonObject realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return false;
        }
        JsonArray rolesArray = realmAccess.getJsonArray("roles");
        return rolesArray != null && containsRole(rolesArray, expectedRole);
    }

    private boolean containsRole(JsonArray rolesArray, String expectedRole) {
        for (JsonValue roleValue : rolesArray) {
            if (roleValue instanceof JsonString jsonString && jsonString.getString().equals(expectedRole)) {
                return true;
            }
        }
        return false;
    }
}
