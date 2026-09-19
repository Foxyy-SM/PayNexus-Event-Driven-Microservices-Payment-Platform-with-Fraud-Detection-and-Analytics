package com.payflow.payment.controller;

import com.payflow.payment.dto.ReconciliationMismatchResponse;
import com.payflow.payment.dto.ReconciliationRunResponse;
import com.payflow.payment.service.ReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/admin/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationController {
    private final ReconciliationService reconciliation;

    public ReconciliationController(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping("/runs")
    public ReconciliationRunResponse trigger() {
        return reconciliation.run("ADMIN");
    }

    @GetMapping("/runs")
    public List<ReconciliationRunResponse> runs() {
        return reconciliation.listRuns();
    }

    @GetMapping("/mismatches")
    public List<ReconciliationMismatchResponse> mismatches() {
        return reconciliation.listMismatches();
    }

    @GetMapping("/runs/{runId}/mismatches")
    public List<ReconciliationMismatchResponse> mismatches(@PathVariable UUID runId) {
        return reconciliation.listMismatches(runId);
    }
}
