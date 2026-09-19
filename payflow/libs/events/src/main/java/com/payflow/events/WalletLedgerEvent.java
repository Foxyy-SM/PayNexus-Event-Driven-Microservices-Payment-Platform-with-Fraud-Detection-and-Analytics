package com.payflow.events;

import java.time.Instant;
import java.util.UUID;

public record WalletLedgerEvent(
        UUID eventId,
        UUID walletId,
        UUID userId,
        UUID paymentId,
        String entryType,
        long amountMinor,
        long availableBalanceMinor,
        long reservedBalanceMinor,
        Instant occurredAt
) {
}
