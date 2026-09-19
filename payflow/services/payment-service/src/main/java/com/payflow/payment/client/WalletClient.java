package com.payflow.payment.client;

import com.payflow.common.security.ServiceTokenProvider;
import com.payflow.payment.dto.WalletView;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import com.payflow.payment.dto.WalletLedgerEntryView;

@Component
public class WalletClient {
    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public WalletClient(RestClient.Builder builder,
                        ServiceTokenProvider tokenProvider,
                        @Value("${payflow.clients.wallet-url}") String walletUrl) {
        this.tokenProvider = tokenProvider;
        this.restClient = builder.baseUrl(walletUrl).build();
    }

    @CircuitBreaker(name = "walletService")
    @Retry(name = "walletService")
    public WalletView getWallet(UUID userId) {
        return restClient.get()
                .uri("/api/v1/wallets/{userId}", userId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(WalletView.class);
    }

    @CircuitBreaker(name = "walletService")
    public WalletView reserve(UUID userId, long amountMinor, UUID paymentId) {
        return restClient.post()
                .uri("/api/v1/wallets/{userId}/reserve", userId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .header("X-User-Id", userId.toString())
                .header("X-Payment-Id", paymentId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amountMinor", amountMinor, "paymentId", paymentId.toString()))
                .retrieve()
                .body(WalletView.class);
    }

    @CircuitBreaker(name = "walletService")
    public void capture(UUID userId, long amountMinor, UUID paymentId) {
        operation(userId, "capture", amountMinor, paymentId);
    }

    @CircuitBreaker(name = "walletService")
    public void release(UUID userId, long amountMinor, UUID paymentId) {
        operation(userId, "release", amountMinor, paymentId);
    }

    @CircuitBreaker(name = "walletService")
    @Retry(name = "walletService")
    public List<WalletLedgerEntryView> phases(UUID paymentId) {
        WalletLedgerEntryView[] result = restClient.get()
                .uri("/api/v1/wallets/ledger/payment/{paymentId}", paymentId)
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .header("X-Payment-Id", paymentId.toString())
                .retrieve()
                .body(WalletLedgerEntryView[].class);
        return result == null ? List.of() : Arrays.asList(result);
    }

    private void operation(UUID userId, String operation, long amountMinor, UUID paymentId) {
        restClient.post()
                .uri("/api/v1/wallets/{userId}/{operation}", userId, operation)
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .header("X-User-Id", userId.toString())
                .header("X-Payment-Id", paymentId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amountMinor", amountMinor, "paymentId", paymentId.toString()))
                .retrieve()
                .toBodilessEntity();
    }

    private String serviceAuth() {
        return tokenProvider.getAuthorizationHeader();
    }
}
