package com.payflow.user.dto;

import com.payflow.user.domain.KycStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateKycRequest(@NotNull KycStatus kycStatus) {
}
