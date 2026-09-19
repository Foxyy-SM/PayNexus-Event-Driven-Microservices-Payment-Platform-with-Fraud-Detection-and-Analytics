package com.payflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceTokenProviderTest {
    @Test
    void postsClientCredentialsCachesTokenAndRefreshesNearExpiry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ServiceTokenProperties properties = new ServiceTokenProperties();
        properties.setTokenUri("https://identity.test/token");
        properties.setClientId("payment-service");
        properties.setClientSecret("secret");
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T00:00:00Z"));
        ServiceTokenProvider provider = new ServiceTokenProvider(builder, properties, clock);

        server.expect(once(), requestTo(properties.getTokenUri()))
                .andExpect(content().string(allOf(
                        containsString("grant_type=client_credentials"),
                        containsString("client_id=payment-service"),
                        containsString("client_secret=secret"))))
                .andRespond(withSuccess("{\"access_token\":\"first\",\"expires_in\":60}", MediaType.APPLICATION_JSON));
        assertThat(provider.getAuthorizationHeader()).isEqualTo("Bearer first");
        assertThat(provider.getAuthorizationHeader()).isEqualTo("Bearer first");
        server.verify();

        clock.instant = clock.instant.plusSeconds(31);
        server.reset();
        server.expect(once(), requestTo(properties.getTokenUri()))
                .andRespond(withSuccess("{\"access_token\":\"second\",\"expires_in\":60}", MediaType.APPLICATION_JSON));
        assertThat(provider.getToken()).isEqualTo("second");
        server.verify();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
