package com.payflow.fraud.service;

import com.payflow.events.FraudCheckedEvent;
import com.payflow.events.Topics;
import com.payflow.fraud.domain.FraudAssessment;
import com.payflow.fraud.dto.ScoreRequest;
import com.payflow.fraud.dto.ScoreResponse;
import com.payflow.fraud.repository.FraudAssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RuleBasedFraudEngine {
    private static final long HIGH_AMOUNT_MINOR = 5_000_000L;
    private static final long CRITICAL_AMOUNT_MINOR = 20_000_000L;

    private final VelocityTracker velocity;
    private final RiskExplanationService explanations;
    private final FraudAssessmentRepository assessments;
    private final OutboxService outbox;

    public RuleBasedFraudEngine(VelocityTracker velocity,
                                RiskExplanationService explanations,
                                FraudAssessmentRepository assessments,
                                OutboxService outbox) {
        this.velocity = velocity;
        this.explanations = explanations;
        this.assessments = assessments;
        this.outbox = outbox;
    }

    @Transactional
    public ScoreResponse score(ScoreRequest request) {
        return assessments.findByPaymentId(request.paymentId())
                .map(this::toResponse)
                .orElseGet(() -> evaluate(request));
    }

    public ScoreResponse analysis(UUID paymentId) {
        return assessments.findByPaymentId(paymentId)
                .map(this::toResponse)
                .orElseThrow(() -> new com.payflow.common.exception.BusinessException(
                        "ASSESSMENT_NOT_FOUND", "No fraud assessment for this payment", 404));
    }

    public List<ScoreResponse> reviewQueue() {
        return assessments.findByDecisionOrderByCreatedAtAsc("REVIEW").stream()
                .map(this::toResponse).toList();
    }

    private ScoreResponse evaluate(ScoreRequest request) {
        List<String> rules = new ArrayList<>();
        double score = 0.05;
        long count = velocity.incrementAndGet(request.userId());
        String previousCountry = velocity.lastCountry(request.userId());

        if (request.amountMinor() > HIGH_AMOUNT_MINOR) {
            score += 0.35;
            rules.add("HIGH_AMOUNT");
        }
        if (request.amountMinor() > CRITICAL_AMOUNT_MINOR) {
            score += 0.25;
            rules.add("CRITICAL_AMOUNT");
        }
        if (count > 5) {
            score += 0.30;
            rules.add("VELOCITY_10M");
        }
        if (previousCountry != null && request.country() != null
                && !previousCountry.equalsIgnoreCase(request.country())
                && request.amountMinor() > HIGH_AMOUNT_MINOR) {
            score += 0.35;
            rules.add("GEO_ANOMALY");
        }
        if (request.country() != null) {
            velocity.rememberCountry(request.userId(), request.country());
        }

        score = Math.min(0.99, score);
        String decision = score >= 0.85 ? "REJECT" : score >= 0.45 ? "REVIEW" : "APPROVE";
        String explanation = explanations.explain(score, decision, rules, request);

        FraudAssessment saved = new FraudAssessment();
        saved.setId(UUID.randomUUID());
        saved.setPaymentId(request.paymentId());
        saved.setUserId(request.userId());
        saved.setAmountMinor(request.amountMinor());
        saved.setRiskScore(score);
        saved.setDecision(decision);
        saved.setExplanation(explanation);
        saved.setTriggeredRules(String.join(",", rules));
        saved.setCreatedAt(Instant.now());
        assessments.save(saved);

        outbox.add(Topics.FRAUD_CHECKED, request.paymentId().toString(),
                new FraudCheckedEvent(UUID.randomUUID(), request.paymentId(), request.userId(),
                        score, decision, explanation, request.amountMinor(), Instant.now()));
        return toResponse(saved);
    }

    private ScoreResponse toResponse(FraudAssessment a) {
        return new ScoreResponse(a.getPaymentId(), a.getAmountMinor(), a.getRiskScore(), a.getDecision(),
                a.getExplanation(), a.getTriggeredRules());
    }
}
