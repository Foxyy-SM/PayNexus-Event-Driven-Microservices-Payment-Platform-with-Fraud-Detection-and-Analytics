package com.payflow.wallet.dto;

import com.payflow.wallet.domain.LedgerEntryType;

import java.time.Instant;
import java.util.UUID;

public record LedgerResponse(
        UUID id,
        UUID paymentId,
        LedgerEntryType entryType,
        long amountMinor,
        long availableBalanceAfterMinor,
        long reservedBalanceAfterMinor,
        Instant createdAt
) {
}
