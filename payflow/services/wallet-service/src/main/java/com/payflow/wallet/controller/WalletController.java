package com.payflow.wallet.controller;

import com.payflow.common.security.PayflowPrincipal;
import com.payflow.wallet.dto.LedgerResponse;
import com.payflow.wallet.dto.MoneyRequest;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.service.WalletMoneyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletMoneyService wallets;

    public WalletController(WalletMoneyService wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/me")
    public WalletResponse me(@AuthenticationPrincipal PayflowPrincipal principal) {
        return wallets.getByUser(UUID.fromString(principal.userId()));
    }

    @GetMapping("/me/ledger")
    public List<LedgerResponse> myLedger(@AuthenticationPrincipal PayflowPrincipal principal) {
        return wallets.history(UUID.fromString(principal.userId()));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE') or #userId.toString() == authentication.name")
    public WalletResponse get(@PathVariable UUID userId) {
        return wallets.getByUser(userId);
    }

    @GetMapping("/{userId}/ledger")
    @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.name")
    public List<LedgerResponse> ledger(@PathVariable UUID userId) {
        return wallets.history(userId);
    }

    @GetMapping("/ledger/payment/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public List<LedgerResponse> paymentPhases(@PathVariable UUID paymentId) {
        return wallets.phases(paymentId);
    }

    @PostMapping("/{userId}/credit")
    @PreAuthorize("hasRole('ADMIN')")
    public WalletResponse credit(@PathVariable UUID userId, @Valid @RequestBody MoneyRequest request) {
        return wallets.credit(userId, request.amountMinor(), request.paymentId());
    }

    @PostMapping("/{userId}/reserve")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public WalletResponse reserve(@PathVariable UUID userId, @Valid @RequestBody MoneyRequest request) {
        return wallets.reserve(userId, request.amountMinor(), request.paymentId());
    }

    @PostMapping("/{userId}/capture")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public WalletResponse capture(@PathVariable UUID userId, @Valid @RequestBody MoneyRequest request) {
        return wallets.capture(userId, request.amountMinor(), request.paymentId());
    }

    @PostMapping("/{userId}/release")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public WalletResponse release(@PathVariable UUID userId, @Valid @RequestBody MoneyRequest request) {
        return wallets.release(userId, request.amountMinor(), request.paymentId());
    }
}
