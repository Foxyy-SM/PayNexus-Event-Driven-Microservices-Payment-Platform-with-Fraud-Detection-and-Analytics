package com.payflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {
    private final KeycloakJwtAuthenticationConverter converter =
            new KeycloakJwtAuthenticationConverter();

    @Test
    void mapsRealmRolesAndIdentityClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("11111111-1111-4111-8111-111111111111")
                .claim("email", "demo@paynexus.local")
                .claim("name", "Demo User")
                .claim("country", "US")
                .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(jwt.getSubject());
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(authentication.getPrincipal())
                .isEqualTo(new PayflowPrincipal(
                        jwt.getSubject(), "demo@paynexus.local", "USER,ADMIN", "Demo User", "US"));
    }

    @Test
    void mapsServiceClientRoleForServiceAuthorization() {
        Jwt jwt = Jwt.withTokenValue("service-token")
                .header("alg", "none")
                .subject("payment-service")
                .claim("preferred_username", "service-account-payment-service")
                .claim("realm_access", Map.of("roles", List.of("SERVICE")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_SERVICE");
        assertThat(authentication.getName()).isEqualTo("payment-service");
        assertThat(((PayflowPrincipal) authentication.getPrincipal()).email())
                .isEqualTo("service-account-payment-service");
    }
}
