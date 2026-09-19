package com.payflow.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(30, 60, 1);
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String user = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (user == null || user.isBlank()) {
                String ip = exchange.getRequest().getRemoteAddress() == null
                        ? "anonymous"
                        : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
                return Mono.just(ip);
            }
            return Mono.just(user);
        };
    }
}
