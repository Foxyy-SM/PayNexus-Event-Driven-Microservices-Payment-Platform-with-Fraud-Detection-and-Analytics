package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record FraudCheckedEvent(
        UUID eventId,
        UUID paymentId,
        UUID userId,
        double riskScore,
        String decision,
        String explanation,
        long amountMinor,
        Instant occurredAt
) {
}
