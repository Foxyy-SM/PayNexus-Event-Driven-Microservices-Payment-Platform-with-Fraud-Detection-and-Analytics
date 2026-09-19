package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID eventId,
        UUID paymentId,
        UUID userId,
        UUID walletId,
        long amountMinor,
        String currency,
        String merchantId,
        Instant occurredAt
) {
}
