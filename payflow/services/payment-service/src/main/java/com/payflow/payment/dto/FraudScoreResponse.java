package com.payflow.payment.dto;

public record FraudScoreResponse(
        double riskScore,
        String decision,
        String explanation
) {
}
