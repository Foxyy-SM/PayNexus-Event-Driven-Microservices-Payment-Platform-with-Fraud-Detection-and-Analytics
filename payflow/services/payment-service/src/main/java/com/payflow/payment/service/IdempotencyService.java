package com.payflow.payment.service;

import com.payflow.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class IdempotencyService {
    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean acquireLock(String userId, String key) {
        Boolean ok = redis.opsForValue().setIfAbsent(lockName(userId, key), "1", Duration.ofSeconds(30));
        return Boolean.TRUE.equals(ok);
    }

    public void rememberPayment(String userId, String key, UUID paymentId) {
        redis.opsForValue().set(resultName(userId, key), paymentId.toString(), Duration.ofHours(24));
    }

    public UUID lookup(String userId, String key) {
        String value = redis.opsForValue().get(resultName(userId, key));
        return value == null ? null : UUID.fromString(value);
    }

    public void releaseLock(String userId, String key) {
        redis.delete(lockName(userId, key));
    }

    public void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED",
                    "Header Idempotency-Key is required for payment creation", 400);
        }
    }

    private String lockName(String userId, String key) {
        return "idem:lock:" + userId + ":" + key;
    }

    private String resultName(String userId, String key) {
        return "idem:result:" + userId + ":" + key;
    }
}
