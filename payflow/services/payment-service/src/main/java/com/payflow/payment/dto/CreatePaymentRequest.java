package com.payflow.payment.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotNull @Positive Long amountMinor,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank String merchantId,
        @Size(min = 2, max = 2) String country
) {
}
