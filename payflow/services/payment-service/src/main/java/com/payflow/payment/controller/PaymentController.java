package com.payflow.payment.controller;

import com.payflow.common.security.PayflowPrincipal;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.service.PaymentWorkflow;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentWorkflow orchestrator;

    public PaymentController(PaymentWorkflow orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@AuthenticationPrincipal PayflowPrincipal principal,
                                  @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
                                  @Valid @RequestBody CreatePaymentRequest request) {
        return orchestrator.create(principal, idempotencyKey, request);
    }

    @GetMapping
    public List<PaymentResponse> list(@AuthenticationPrincipal PayflowPrincipal principal) {
        return orchestrator.listMine(principal);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id, @AuthenticationPrincipal PayflowPrincipal principal) {
        return orchestrator.get(id, principal);
    }

    @GetMapping("/admin/review-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentResponse> reviewQueue() {
        return orchestrator.reviewQueue();
    }

    @PostMapping("/{id}/review/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentResponse approve(@PathVariable UUID id) {
        return orchestrator.approve(id);
    }

    @PostMapping("/{id}/review/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentResponse reject(@PathVariable UUID id,
                                  @RequestBody(required = false) java.util.Map<String, String> body) {
        return orchestrator.reject(id, body == null ? null : body.get("reason"));
    }
}
