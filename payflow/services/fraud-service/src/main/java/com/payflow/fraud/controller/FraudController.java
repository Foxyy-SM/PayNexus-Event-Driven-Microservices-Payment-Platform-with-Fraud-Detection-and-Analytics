package com.payflow.fraud.controller;

import com.payflow.fraud.dto.PolicyAskRequest;
import com.payflow.fraud.dto.PolicyAskResponse;
import com.payflow.fraud.dto.ScoreRequest;
import com.payflow.fraud.dto.ScoreResponse;
import com.payflow.fraud.service.PolicyRagService;
import com.payflow.fraud.service.RuleBasedFraudEngine;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {
    private final RuleBasedFraudEngine engine;
    private final PolicyRagService policies;

    public FraudController(RuleBasedFraudEngine engine, PolicyRagService policies) {
        this.engine = engine;
        this.policies = policies;
    }

    @PostMapping("/score")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
    public ScoreResponse score(@Valid @RequestBody ScoreRequest request) {
        return engine.score(request);
    }

    @GetMapping("/transactions/{paymentId}/risk-analysis")
    public ScoreResponse analysis(@PathVariable UUID paymentId) {
        return engine.analysis(paymentId);
    }

    @GetMapping("/admin/review-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ScoreResponse> reviewQueue() {
        return engine.reviewQueue();
    }

    @PostMapping("/policies/ask")
    public PolicyAskResponse ask(@RequestBody PolicyAskRequest request) {
        return policies.ask(request.question());
    }
}
