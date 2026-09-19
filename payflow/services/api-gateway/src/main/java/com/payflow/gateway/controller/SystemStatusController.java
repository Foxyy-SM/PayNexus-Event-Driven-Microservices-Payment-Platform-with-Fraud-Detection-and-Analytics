package com.payflow.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {
    private final WebClient.Builder webClient;
    private final Map<String, String> services;

    public SystemStatusController(
            WebClient.Builder webClient,
            @Value("${payflow.services.user-url}") String userUrl,
            @Value("${payflow.services.payment-url}") String paymentUrl,
            @Value("${payflow.services.wallet-url}") String walletUrl,
            @Value("${payflow.services.fraud-url}") String fraudUrl,
            @Value("${payflow.services.notification-url}") String notificationUrl,
            @Value("${payflow.services.transaction-url}") String transactionUrl) {
        this.webClient = webClient;
        this.services = new LinkedHashMap<>();
        services.put("user", userUrl);
        services.put("payment", paymentUrl);
        services.put("wallet", walletUrl);
        services.put("fraud", fraudUrl);
        services.put("notification", notificationUrl);
        services.put("transaction", transactionUrl);
    }

    @GetMapping("/status")
    public Mono<Map<String, Object>> status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Mono<Map<String, Object>> health = Flux.fromIterable(services.entrySet())
                .flatMap(entry -> health(entry.getKey(), entry.getValue()))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, LinkedHashMap::new);
        Mono<Object> paymentCircuits = get(services.get("payment") + "/actuator/circuitbreakers", authorization)
                .cast(Object.class)
                .onErrorReturn(Map.of("status", "RESTRICTED_OR_UNAVAILABLE"));

        return Mono.zip(health, paymentCircuits)
                .map(result -> Map.of(
                        "timestamp", Instant.now().toString(),
                        "services", result.getT1(),
                        "paymentCircuits", result.getT2()));
    }

    private Mono<Map.Entry<String, Object>> health(String name, String baseUrl) {
        return get(baseUrl + "/actuator/health", null)
                .map(body -> Map.entry(name, (Object) body))
                .onErrorReturn(Map.entry(name, Map.of("status", "UNAVAILABLE")));
    }

    private Mono<Map<String, Object>> get(String uri, String authorization) {
        return webClient.build().get().uri(uri)
                .headers(headers -> {
                    if (authorization != null && !authorization.isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(2));
    }
}
