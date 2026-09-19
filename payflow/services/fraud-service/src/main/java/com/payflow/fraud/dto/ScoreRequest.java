package com.payflow.fraud.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ScoreRequest(
        @NotNull UUID paymentId,
        @NotNull UUID userId,
        @NotNull @Positive Long amountMinor,
        String country,
        String merchantId
) {
}
