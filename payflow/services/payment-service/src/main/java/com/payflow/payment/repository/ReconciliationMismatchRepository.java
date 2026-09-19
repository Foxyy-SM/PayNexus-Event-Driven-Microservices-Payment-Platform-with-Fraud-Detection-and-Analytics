package com.payflow.payment.repository;

import com.payflow.payment.domain.ReconciliationMismatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReconciliationMismatchRepository extends JpaRepository<ReconciliationMismatch, UUID> {
    List<ReconciliationMismatch> findByRunIdOrderByDetectedAtAsc(UUID runId);
    List<ReconciliationMismatch> findAllByOrderByDetectedAtDesc();
}
