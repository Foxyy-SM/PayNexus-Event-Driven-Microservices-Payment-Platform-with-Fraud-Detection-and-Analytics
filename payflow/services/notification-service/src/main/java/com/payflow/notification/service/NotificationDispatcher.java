package com.payflow.notification.service;

import com.payflow.events.NotificationRequestedEvent;
import com.payflow.notification.domain.NotificationRecord;
import com.payflow.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private final NotificationRepository repository;

    public NotificationDispatcher(NotificationRepository repository) {
        this.repository = repository;
    }

    public void dispatch(NotificationRequestedEvent event) {
        NotificationRecord record = new NotificationRecord();
        record.setId(event.eventId());
        record.setUserId(event.userId());
        record.setChannel(event.channel());
        record.setTemplate(event.template());
        record.setDestination(event.destination());
        record.setPayloadJson(event.payloadJson());
        record.setAttempts(1);
        record.setCreatedAt(Instant.now());
        try {
            log.info("Sending {} notification to {} template={}", event.channel(), event.destination(), event.template());
            record.setStatus("SENT");
            record.setSentAt(Instant.now());
            repository.save(record);
        } catch (RuntimeException ex) {
            record.setStatus("FAILED");
            record.setLastError(ex.getMessage());
            repository.save(record);
            throw ex;
        }
    }

    public List<NotificationRecord> forUser(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
