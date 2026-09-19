package com.payflow.tx.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.events.PaymentCompletedEvent;
import com.payflow.events.PaymentFailedEvent;
import com.payflow.events.PaymentInitiatedEvent;
import com.payflow.tx.domain.ReportedTransaction;
import com.payflow.tx.repository.ReportedTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionQueryService {
    private final ReportedTransactionRepository repository;

    public TransactionQueryService(ReportedTransactionRepository repository) {
        this.repository = repository;
    }

    public void onInitiated(PaymentInitiatedEvent event) {
        ReportedTransaction tx = repository.findById(event.paymentId()).orElseGet(ReportedTransaction::new);
        tx.setPaymentId(event.paymentId());
        tx.setUserId(event.userId());
        tx.setAmountMinor(event.amountMinor());
        tx.setCurrency(event.currency());
        tx.setMerchantId(event.merchantId());
        tx.setStatus("INITIATED");
        tx.setCreatedAt(event.occurredAt());
        repository.save(tx);
    }

    public void onCompleted(PaymentCompletedEvent event) {
        ReportedTransaction tx = repository.findById(event.paymentId()).orElseGet(ReportedTransaction::new);
        tx.setPaymentId(event.paymentId());
        tx.setUserId(event.userId());
        tx.setAmountMinor(event.amountMinor());
        tx.setCurrency(event.currency());
        tx.setMerchantId(event.merchantId());
        tx.setStatus("COMPLETED");
        tx.setCompletedAt(event.occurredAt());
        if (tx.getCreatedAt() == null) {
            tx.setCreatedAt(event.occurredAt());
        }
        repository.save(tx);
    }

    public void onFailed(PaymentFailedEvent event) {
        ReportedTransaction tx = repository.findById(event.paymentId()).orElseGet(ReportedTransaction::new);
        tx.setPaymentId(event.paymentId());
        tx.setUserId(event.userId());
        tx.setStatus("FAILED");
        tx.setFailureReason(event.reason());
        if (tx.getCreatedAt() == null) {
            tx.setCreatedAt(event.occurredAt());
        }
        repository.save(tx);
    }

    public ReportedTransaction get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("TX_NOT_FOUND", "Transaction not found", 404));
    }

    public Page<ReportedTransaction> search(UUID userId, String status, Instant from, Instant to, String search, Pageable pageable) {
        Specification<ReportedTransaction> spec = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("merchantId")), pattern));
        }
        return repository.findAll(spec, pageable);
    }
}
