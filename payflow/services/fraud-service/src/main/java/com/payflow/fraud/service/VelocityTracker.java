package com.payflow.fraud.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class VelocityTracker {
    private final StringRedisTemplate redis;

    public VelocityTracker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long incrementAndGet(UUID userId) {
        String key = "fraud:velocity:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(10));
        }
        return count == null ? 1 : count;
    }

    public String lastCountry(UUID userId) {
        return redis.opsForValue().get("fraud:country:" + userId);
    }

    public void rememberCountry(UUID userId, String country) {
        redis.opsForValue().set("fraud:country:" + userId, country, Duration.ofHours(24));
    }
}
