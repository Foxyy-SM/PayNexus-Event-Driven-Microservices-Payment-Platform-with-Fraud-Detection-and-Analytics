package com.payflow.tx.controller;

import com.payflow.common.security.PayflowPrincipal;
import com.payflow.tx.domain.ReportedTransaction;
import com.payflow.tx.service.TransactionQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionQueryService service;

    public TransactionController(TransactionQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ReportedTransaction> search(@AuthenticationPrincipal PayflowPrincipal principal,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                            @RequestParam(required = false) String query,
                                            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        UUID userId = principal.roles() != null && principal.roles().contains("ADMIN")
                ? null
                : UUID.fromString(principal.userId());
        if (principal.roles() != null && principal.roles().contains("ADMIN")) {
            return service.search(null, status, from, to, query, pageable);
        }
        return service.search(userId, status, from, to, query, pageable);
    }

    @GetMapping("/{id}")
    public ReportedTransaction get(@PathVariable UUID id) {
        return service.get(id);
    }
}
