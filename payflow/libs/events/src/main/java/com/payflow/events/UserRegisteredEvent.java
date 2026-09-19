package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String country,
        Instant occurredAt
) {
}
