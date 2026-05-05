package com.github.swim_developer.framework.provider.unit.infrastructure;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.infrastructure.out.security.JwtRoleValidator;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("infrastructure")
@ExtendWith(TestNameLoggerExtension.class)
class JwtRoleValidatorTest {

    private JwtRoleValidator validator;
    private JsonWebToken jwt;

    @BeforeEach
    void setUp() {
        jwt = mock(JsonWebToken.class);
        validator = new JwtRoleValidator(jwt, "-amq");
    }

    @Test
    void getUsernameReturnsNameWhenPresent() {
        when(jwt.getName()).thenReturn("swim-user");

        assertThat(validator.getUsername()).isEqualTo("swim-user");
    }

    @Test
    void getUsernameFallsBackToPreferredUsername() {
        when(jwt.getName()).thenReturn(null);
        when(jwt.<String>getClaim("preferred_username")).thenReturn("preferred-user");

        assertThat(validator.getUsername()).isEqualTo("preferred-user");
    }

    @Test
    void getUsernameFallsBackToSubject() {
        when(jwt.getName()).thenReturn("");
        when(jwt.<String>getClaim("preferred_username")).thenReturn("  ");
        when(jwt.getSubject()).thenReturn("sub-12345");

        assertThat(validator.getUsername()).isEqualTo("sub-12345");
    }

    @Test
    void getExpectedAmqRoleAppendsConfiguredSuffix() {
        assertThat(validator.getExpectedAmqRole("ansp-client"))
                .isEqualTo("ansp-client-amq");
    }

    @Test
    void hasAmqRoleReturnsTrueWhenRoleInResourceAccess() {
        JsonObject resourceAccess = Json.createObjectBuilder()
                .add("artemis-broker", Json.createObjectBuilder()
                        .add("roles", Json.createArrayBuilder()
                                .add("swim-user-amq")))
                .build();
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(resourceAccess);
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(null);

        assertThat(validator.hasAmqRole("swim-user")).isTrue();
    }

    @Test
    void hasAmqRoleReturnsTrueWhenRoleInRealmAccess() {
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(null);
        JsonObject realmAccess = Json.createObjectBuilder()
                .add("roles", Json.createArrayBuilder()
                        .add("ansp-amq"))
                .build();
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(realmAccess);

        assertThat(validator.hasAmqRole("ansp")).isTrue();
    }

    @Test
    void hasAmqRoleReturnsFalseWhenRoleMissing() {
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(null);
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(null);

        assertThat(validator.hasAmqRole("swim-user")).isFalse();
    }

    @Test
    void hasAmqRoleReturnsFalseWhenNoMatchingRole() {
        JsonObject realmAccess = Json.createObjectBuilder()
                .add("roles", Json.createArrayBuilder()
                        .add("other-role")
                        .add("admin"))
                .build();
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(null);
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(realmAccess);

        assertThat(validator.hasAmqRole("swim-user")).isFalse();
    }

    @Test
    void hasAmqRoleHandlesResourceAccessWithoutRolesArray() {
        JsonObject resourceAccess = Json.createObjectBuilder()
                .add("client-1", Json.createObjectBuilder()
                        .add("other", "value"))
                .build();
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(resourceAccess);
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(null);

        assertThat(validator.hasAmqRole("swim-user")).isFalse();
    }

    @Test
    void hasAmqRoleHandlesRealmAccessWithoutRolesArray() {
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(null);
        JsonObject realmAccess = Json.createObjectBuilder().build();
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(realmAccess);

        assertThat(validator.hasAmqRole("swim-user")).isFalse();
    }

    @Test
    void validateAmqRoleSucceedsWhenRolePresent() {
        JsonObject realmAccess = Json.createObjectBuilder()
                .add("roles", Json.createArrayBuilder().add("user-amq"))
                .build();
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(null);
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(realmAccess);

        validator.validateAmqRole("user");
    }

    @Test
    void validateAmqRoleThrowsSecurityExceptionWhenRoleMissing() {
        when(jwt.<JsonObject>getClaim("resource_access")).thenReturn(null);
        when(jwt.<JsonObject>getClaim("realm_access")).thenReturn(null);

        assertThatThrownBy(() -> validator.validateAmqRole("swim-client"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("swim-client")
                .hasMessageContaining("swim-client-amq");
    }
}
