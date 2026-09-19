package com.payflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.OutboxEvent;
import com.payflow.payment.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void add(String topic, String key, Object event) {
        OutboxEvent row = new OutboxEvent();
        row.setId(UUID.randomUUID());
        row.setTopic(topic);
        row.setEventKey(key);
        row.setEventType(event.getClass().getName());
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize outbox event", ex);
        }
        row.setCreatedAt(Instant.now());
        row.setNextAttemptAt(Instant.now());
        repository.save(row);
    }
}
