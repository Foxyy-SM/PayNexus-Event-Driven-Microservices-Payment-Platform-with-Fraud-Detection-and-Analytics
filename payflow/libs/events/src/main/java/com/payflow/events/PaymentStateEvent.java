package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentStateEvent(
        UUID eventId,
        UUID paymentId,
        UUID userId,
        long amountMinor,
        String currency,
        String status,
        Instant occurredAt
) {
}
