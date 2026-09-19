package com.payflow.payment.repository;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Payment> findByStatusOrderByCreatedAtAsc(PaymentStatus status);
    long countByUserIdAndStatus(UUID userId, PaymentStatus status);
}
