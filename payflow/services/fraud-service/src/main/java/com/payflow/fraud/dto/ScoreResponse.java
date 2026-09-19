package com.payflow.fraud.dto;

import java.util.UUID;

public record ScoreResponse(
        UUID paymentId,
        long amountMinor,
        double riskScore,
        String decision,
        String explanation,
        String triggeredRules
) {
}
