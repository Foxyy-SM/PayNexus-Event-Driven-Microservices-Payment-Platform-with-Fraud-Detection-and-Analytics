package com.payflow.payment.dto;

import java.time.Instant;
import java.util.UUID;

public record WalletLedgerEntryView(
        UUID id, UUID paymentId, String entryType, long amountMinor,
        long availableBalanceAfterMinor, long reservedBalanceAfterMinor, Instant createdAt) {
}
