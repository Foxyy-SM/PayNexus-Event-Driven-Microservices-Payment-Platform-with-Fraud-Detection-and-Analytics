package com.payflow.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_mismatches")
public class ReconciliationMismatch {
    public enum Type {
        COMPLETED_CAPTURE_MISSING_OR_WRONG,
        TERMINAL_RELEASE_MISSING,
        REVIEW_PHASE_INVALID,
        STALE_PENDING_REVIEW,
        STALE_CAPTURING
    }
    public enum Action { NONE, RELEASE, CAPTURE }
    public enum RepairStatus { NOT_APPLICABLE, PENDING, SUCCEEDED, FAILED }

    @Id private UUID id;
    @Column(nullable = false) private UUID runId;
    @Column(nullable = false) private UUID paymentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Type mismatchType;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus paymentStatus;
    @Column(nullable = false) private String expectedEvidence;
    @Column(nullable = false) private String actualEvidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Action repairAction;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RepairStatus repairStatus;
    private String repairDetail;
    @Column(nullable = false) private Instant detectedAt;
    private Instant repairedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public Type getMismatchType() { return mismatchType; }
    public void setMismatchType(Type value) { mismatchType = value; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus value) { paymentStatus = value; }
    public String getExpectedEvidence() { return expectedEvidence; }
    public void setExpectedEvidence(String value) { expectedEvidence = value; }
    public String getActualEvidence() { return actualEvidence; }
    public void setActualEvidence(String value) { actualEvidence = value; }
    public Action getRepairAction() { return repairAction; }
    public void setRepairAction(Action value) { repairAction = value; }
    public RepairStatus getRepairStatus() { return repairStatus; }
    public void setRepairStatus(RepairStatus value) { repairStatus = value; }
    public String getRepairDetail() { return repairDetail; }
    public void setRepairDetail(String value) { repairDetail = value; }
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant value) { detectedAt = value; }
    public Instant getRepairedAt() { return repairedAt; }
    public void setRepairedAt(Instant value) { repairedAt = value; }
}
