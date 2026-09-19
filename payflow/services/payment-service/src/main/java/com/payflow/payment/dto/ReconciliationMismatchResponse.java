package com.payflow.payment.dto;

import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.domain.ReconciliationMismatch;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationMismatchResponse(
        UUID id, UUID runId, UUID paymentId, ReconciliationMismatch.Type mismatchType,
        PaymentStatus paymentStatus, String expectedEvidence, String actualEvidence,
        ReconciliationMismatch.Action repairAction, ReconciliationMismatch.RepairStatus repairStatus,
        String repairDetail, Instant detectedAt, Instant repairedAt) {
}
