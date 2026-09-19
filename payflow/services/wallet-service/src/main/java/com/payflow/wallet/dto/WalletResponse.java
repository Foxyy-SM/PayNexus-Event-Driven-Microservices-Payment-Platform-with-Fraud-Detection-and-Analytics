package com.payflow.wallet.dto;

import java.util.UUID;

public record WalletResponse(
        UUID walletId,
        UUID userId,
        long availableBalanceMinor,
        long reservedBalanceMinor,
        String currency,
        Long version
) {
}
