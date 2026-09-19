package com.payflow.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private long availableBalanceMinor;

    @Column(nullable = false)
    private long reservedBalanceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public long getAvailableBalanceMinor() { return availableBalanceMinor; }
    public void setAvailableBalanceMinor(long availableBalanceMinor) { this.availableBalanceMinor = availableBalanceMinor; }
    public long getReservedBalanceMinor() { return reservedBalanceMinor; }
    public void setReservedBalanceMinor(long reservedBalanceMinor) { this.reservedBalanceMinor = reservedBalanceMinor; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
