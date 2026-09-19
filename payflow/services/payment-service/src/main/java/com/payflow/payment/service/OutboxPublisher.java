package com.payflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.OutboxEvent;
import com.payflow.payment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final KafkaOperations<String, Object> kafka;
    private final MeterRegistry metrics;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper,
                           KafkaOperations<String, Object> kafka, MeterRegistry metrics) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.kafka = kafka;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-ms:500}")
    @Transactional
    public void publish() {
        for (OutboxEvent row : repository.findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                Instant.now(), PageRequest.of(0, 100))) {
            try {
                Object event = objectMapper.readValue(row.getPayload(), Class.forName(row.getEventType()));
                kafka.send(row.getTopic(), row.getEventKey(), event).get(10, TimeUnit.SECONDS);
                row.setPublishedAt(Instant.now());
                metrics.counter("payflow.outbox.published").increment();
            } catch (Exception ex) {
                row.setAttempts(row.getAttempts() + 1);
                long delay = Math.min(300, 1L << Math.min(row.getAttempts(), 8));
                row.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(delay)));
                row.setLastError(ex.getMessage() == null ? ex.getClass().getSimpleName()
                        : ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())));
                metrics.counter("payflow.outbox.failures").increment();
            }
            repository.save(row);
        }
    }
}
