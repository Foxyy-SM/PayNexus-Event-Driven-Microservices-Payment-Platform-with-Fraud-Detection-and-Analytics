package com.payflow.user.dto;

import com.payflow.user.domain.KycStatus;
import com.payflow.user.domain.Role;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String country,
        Role role,
        KycStatus kycStatus,
        Instant createdAt
) {
}
