package com.payflow.notification.repository;

import com.payflow.notification.domain.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {
    List<NotificationRecord> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
