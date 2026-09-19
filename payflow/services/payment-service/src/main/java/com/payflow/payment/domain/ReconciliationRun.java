package com.payflow.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {
    public enum Status { RUNNING, COMPLETED, FAILED }

    @Id private UUID id;
    @Column(nullable = false) private String triggerSource;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private long staleThresholdSeconds;
    @Column(nullable = false) private Instant startedAt;
    private Instant completedAt;
    @Column(nullable = false) private int paymentsScanned;
    @Column(nullable = false) private int mismatchesFound;
    @Column(nullable = false) private int repairsSucceeded;
    @Column(nullable = false) private int repairsFailed;
    private String errorMessage;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getStaleThresholdSeconds() { return staleThresholdSeconds; }
    public void setStaleThresholdSeconds(long value) { staleThresholdSeconds = value; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public int getPaymentsScanned() { return paymentsScanned; }
    public void setPaymentsScanned(int value) { paymentsScanned = value; }
    public int getMismatchesFound() { return mismatchesFound; }
    public void setMismatchesFound(int value) { mismatchesFound = value; }
    public int getRepairsSucceeded() { return repairsSucceeded; }
    public void setRepairsSucceeded(int value) { repairsSucceeded = value; }
    public int getRepairsFailed() { return repairsFailed; }
    public void setRepairsFailed(int value) { repairsFailed = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
