package com.payflow.tx.kafka;

import com.payflow.events.PaymentCompletedEvent;
import com.payflow.events.PaymentFailedEvent;
import com.payflow.events.PaymentInitiatedEvent;
import com.payflow.events.Topics;
import com.payflow.tx.service.TransactionQueryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListeners {
    private final TransactionQueryService service;

    public PaymentEventListeners(TransactionQueryService service) {
        this.service = service;
    }

    @KafkaListener(topics = Topics.PAYMENT_INITIATED, groupId = "transaction-service")
    public void initiated(PaymentInitiatedEvent event) {
        service.onInitiated(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED, groupId = "transaction-service")
    public void completed(PaymentCompletedEvent event) {
        service.onCompleted(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "transaction-service")
    public void failed(PaymentFailedEvent event) {
        service.onFailed(event);
    }
}
