package com.payflow.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID walletId;

    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType entryType;

    @Column(nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private long availableBalanceAfterMinor;

    @Column(nullable = false)
    private long reservedBalanceAfterMinor;

    @Column(nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWalletId() { return walletId; }
    public void setWalletId(UUID walletId) { this.walletId = walletId; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public LedgerEntryType getEntryType() { return entryType; }
    public void setEntryType(LedgerEntryType entryType) { this.entryType = entryType; }
    public long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(long amountMinor) { this.amountMinor = amountMinor; }
    public long getAvailableBalanceAfterMinor() { return availableBalanceAfterMinor; }
    public void setAvailableBalanceAfterMinor(long value) { this.availableBalanceAfterMinor = value; }
    public long getReservedBalanceAfterMinor() { return reservedBalanceAfterMinor; }
    public void setReservedBalanceAfterMinor(long value) { this.reservedBalanceAfterMinor = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
