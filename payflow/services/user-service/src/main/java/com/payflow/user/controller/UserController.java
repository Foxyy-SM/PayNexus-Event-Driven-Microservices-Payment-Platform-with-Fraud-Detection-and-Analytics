package com.payflow.user.controller;

import com.payflow.common.security.PayflowPrincipal;
import com.payflow.user.dto.UpdateKycRequest;
import com.payflow.user.dto.UserProfileResponse;
import com.payflow.user.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final IdentityService identityService;

    public UserController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal PayflowPrincipal principal) {
        return identityService.me(principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @identityService.isOwner(#id, authentication.name)")
    public UserProfileResponse get(@PathVariable UUID id) {
        return identityService.getById(id);
    }

    @PatchMapping("/{id}/kyc")
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse updateKyc(@PathVariable UUID id, @Valid @RequestBody UpdateKycRequest request) {
        return identityService.updateKyc(id, request);
    }
}
