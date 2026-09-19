package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record NotificationRequestedEvent(
        UUID eventId,
        UUID userId,
        String channel,
        String template,
        String destination,
        String payloadJson,
        Instant occurredAt
) {
}
