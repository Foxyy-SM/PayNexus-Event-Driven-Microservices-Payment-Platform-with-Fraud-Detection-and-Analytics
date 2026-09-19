package com.payflow.common.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;

public class ServiceTokenProvider {
    private static final long REFRESH_SKEW_SECONDS = 30;

    private final RestClient restClient;
    private final ServiceTokenProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public ServiceTokenProvider(
            RestClient.Builder builder,
            ServiceTokenProperties properties) {
        this(builder, properties, Clock.systemUTC());
    }

    ServiceTokenProvider(
            RestClient.Builder builder,
            ServiceTokenProperties properties,
            Clock clock) {
        this.restClient = builder.build();
        this.properties = properties;
        this.clock = clock;
    }

    public String getToken() {
        CachedToken current = cachedToken;
        if (current != null && current.usableAt(clock.instant())) {
            return current.value();
        }
        return refreshToken();
    }

    public String getAuthorizationHeader() {
        return "Bearer " + getToken();
    }

    private synchronized String refreshToken() {
        Instant now = clock.instant();
        if (cachedToken != null && cachedToken.usableAt(now)) {
            return cachedToken.value();
        }

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        TokenResponse response = restClient.post()
                .uri(properties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Keycloak returned no access token");
        }
        long usableSeconds = Math.max(1, response.expiresIn() - REFRESH_SKEW_SECONDS);
        cachedToken = new CachedToken(response.accessToken(), now.plusSeconds(usableSeconds));
        return response.accessToken();
    }

    private record CachedToken(String value, Instant refreshAt) {
        boolean usableAt(Instant now) {
            return now.isBefore(refreshAt);
        }
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
