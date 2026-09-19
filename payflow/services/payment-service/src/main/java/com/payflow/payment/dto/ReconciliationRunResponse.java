package com.payflow.payment.dto;

import com.payflow.payment.domain.ReconciliationRun;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationRunResponse(
        UUID id, String triggerSource, ReconciliationRun.Status status, long staleThresholdSeconds,
        Instant startedAt, Instant completedAt, int paymentsScanned, int mismatchesFound,
        int repairsSucceeded, int repairsFailed, String errorMessage) {
}
