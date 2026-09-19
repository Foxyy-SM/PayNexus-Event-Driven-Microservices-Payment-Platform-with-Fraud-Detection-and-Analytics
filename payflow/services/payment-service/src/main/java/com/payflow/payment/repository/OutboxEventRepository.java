package com.payflow.payment.repository;

import com.payflow.payment.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            Instant now, Pageable pageable);
}
