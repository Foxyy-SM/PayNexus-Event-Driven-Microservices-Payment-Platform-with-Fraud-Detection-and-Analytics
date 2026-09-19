package com.payflow.tx.repository;

import com.payflow.tx.domain.ReportedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ReportedTransactionRepository extends JpaRepository<ReportedTransaction, UUID>,
        JpaSpecificationExecutor<ReportedTransaction> {
}
