package com.payflow.payment.client;

import com.payflow.common.security.ServiceTokenProvider;
import com.payflow.payment.dto.FraudScoreResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class FraudClient {
    private static final Logger log = LoggerFactory.getLogger(FraudClient.class);
    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public FraudClient(RestClient.Builder builder,
                       ServiceTokenProvider tokenProvider,
                       @Value("${payflow.clients.fraud-url}") String fraudUrl) {
        this.tokenProvider = tokenProvider;
        this.restClient = builder.baseUrl(fraudUrl).build();
    }

    @CircuitBreaker(name = "fraudService", fallbackMethod = "fallback")
    @Retry(name = "fraudService")
    public FraudScoreResponse score(UUID paymentId, UUID userId, long amountMinor, String country, String merchantId) {
        return restClient.post()
                .uri("/api/v1/fraud/score")
                .header(HttpHeaders.AUTHORIZATION, serviceAuth())
                .header("X-Payment-Id", paymentId.toString())
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "paymentId", paymentId.toString(),
                        "userId", userId.toString(),
                        "amountMinor", amountMinor,
                        "country", country,
                        "merchantId", merchantId
                ))
                .retrieve()
                .body(FraudScoreResponse.class);
    }

    @SuppressWarnings("unused")
    public FraudScoreResponse fallback(UUID paymentId, UUID userId, long amountMinor, String country,
                                      String merchantId, Throwable ex) {
        log.warn("Fraud service degraded for payment {}: {}", paymentId, ex.toString());
        return new FraudScoreResponse(0.4, "REVIEW",
                "Fraud service unavailable. Payment continues in degraded mode and is marked for manual review.");
    }

    private String serviceAuth() {
        return tokenProvider.getAuthorizationHeader();
    }
}
