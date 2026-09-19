package com.payflow.payment.dto;

import java.util.UUID;

public record WalletView(UUID walletId, UUID userId, long availableBalanceMinor,
                         long reservedBalanceMinor, String currency, Long version) {
}
