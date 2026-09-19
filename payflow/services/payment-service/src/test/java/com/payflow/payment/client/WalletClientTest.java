package com.payflow.payment.client;

import com.payflow.common.security.ServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WalletClientTest {
    @Test
    void sendsServiceBearerTokenAndCorrelationHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        when(tokens.getAuthorizationHeader()).thenReturn("Bearer service-jwt");
        WalletClient client = new WalletClient(builder, tokens, "https://wallet.test");
        UUID user = UUID.randomUUID();
        UUID payment = UUID.randomUUID();

        server.expect(requestTo("https://wallet.test/api/v1/wallets/" + user + "/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-jwt"))
                .andExpect(header("X-User-Id", user.toString()))
                .andExpect(header("X-Payment-Id", payment.toString()))
                .andRespond(withSuccess("""
                        {"walletId":"%s","userId":"%s","availableBalanceMinor":900,
                         "reservedBalanceMinor":100,"currency":"INR","version":1}
                        """.formatted(UUID.randomUUID(), user), MediaType.APPLICATION_JSON));

        var response = client.reserve(user, 100, payment);

        assertThat(response.userId()).isEqualTo(user);
        assertThat(response.reservedBalanceMinor()).isEqualTo(100);
        server.verify();
    }
}
