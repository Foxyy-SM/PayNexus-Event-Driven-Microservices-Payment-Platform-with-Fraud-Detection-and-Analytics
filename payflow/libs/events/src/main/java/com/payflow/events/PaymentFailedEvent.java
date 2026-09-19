package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        UUID paymentId,
        UUID userId,
        String reason,
        String status,
        Instant occurredAt
) {
}
