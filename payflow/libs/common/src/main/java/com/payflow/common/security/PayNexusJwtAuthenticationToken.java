package com.payflow.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

public final class PayNexusJwtAuthenticationToken extends JwtAuthenticationToken {
    private final PayflowPrincipal principal;

    public PayNexusJwtAuthenticationToken(
            Jwt jwt,
            Collection<? extends GrantedAuthority> authorities,
            PayflowPrincipal principal) {
        super(jwt, authorities, principal.userId());
        this.principal = principal;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
