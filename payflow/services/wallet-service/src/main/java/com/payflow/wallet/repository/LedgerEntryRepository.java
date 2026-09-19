package com.payflow.wallet.repository;

import com.payflow.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
    List<LedgerEntry> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
    Optional<LedgerEntry> findByPaymentIdAndEntryType(UUID paymentId, com.payflow.wallet.domain.LedgerEntryType type);
}
