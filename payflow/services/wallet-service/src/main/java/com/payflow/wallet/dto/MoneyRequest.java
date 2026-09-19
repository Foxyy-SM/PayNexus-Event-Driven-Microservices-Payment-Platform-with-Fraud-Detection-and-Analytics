package com.payflow.wallet.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoneyRequest(
        @NotNull @Positive Long amountMinor,
        @NotNull UUID paymentId
) {
}
