package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentInitiatedEvent(
        UUID eventId,
        UUID paymentId,
        UUID userId,
        long amountMinor,
        String currency,
        String merchantId,
        String country,
        String idempotencyKey,
        Instant occurredAt
) {
}
