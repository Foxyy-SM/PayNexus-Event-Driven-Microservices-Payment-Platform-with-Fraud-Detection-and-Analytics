package com.payflow.fraud.kafka;

import com.payflow.events.PaymentInitiatedEvent;
import com.payflow.events.Topics;
import com.payflow.fraud.dto.ScoreRequest;
import com.payflow.fraud.service.RuleBasedFraudEngine;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentInitiatedListener {
    private final RuleBasedFraudEngine engine;

    public PaymentInitiatedListener(RuleBasedFraudEngine engine) {
        this.engine = engine;
    }

    @KafkaListener(topics = Topics.PAYMENT_INITIATED, groupId = "fraud-service")
    public void onInitiated(PaymentInitiatedEvent event) {
        engine.score(new ScoreRequest(event.paymentId(), event.userId(), event.amountMinor(),
                event.country(), event.merchantId()));
    }
}
