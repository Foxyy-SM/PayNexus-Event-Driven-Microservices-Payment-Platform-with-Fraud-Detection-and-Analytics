package com.payflow.payment.dto;

import com.payflow.payment.domain.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID userId,
        long amountMinor,
        String currency,
        String merchantId,
        PaymentStatus status,
        Double riskScore,
        String fraudDecision,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {
}
