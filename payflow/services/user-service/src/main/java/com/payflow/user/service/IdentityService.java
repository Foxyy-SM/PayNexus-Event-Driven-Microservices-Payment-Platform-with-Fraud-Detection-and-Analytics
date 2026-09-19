package com.payflow.user.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.PayflowPrincipal;
import com.payflow.events.Topics;
import com.payflow.events.UserRegisteredEvent;
import com.payflow.user.domain.KycStatus;
import com.payflow.user.domain.Role;
import com.payflow.user.domain.UserAccount;
import com.payflow.user.dto.UpdateKycRequest;
import com.payflow.user.dto.UserProfileResponse;
import com.payflow.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class IdentityService {
    private final UserAccountRepository users;
    private final OutboxService outbox;

    public IdentityService(UserAccountRepository users,
                           OutboxService outbox) {
        this.users = users;
        this.outbox = outbox;
    }

    @Transactional
    public UserProfileResponse me(PayflowPrincipal principal) {
        UserAccount user = users.findByKeycloakSubject(principal.userId())
                .orElseGet(() -> provision(principal));
        synchronizeClaims(user, principal);
        return toProfile(users.save(user));
    }

    public UserProfileResponse getById(UUID id) {
        return toProfile(load(id));
    }

    @Transactional
    public UserProfileResponse updateKyc(UUID id, UpdateKycRequest request) {
        UserAccount user = load(id);
        user.setKycStatus(request.kycStatus());
        user.setUpdatedAt(Instant.now());
        return toProfile(users.save(user));
    }

    public boolean isOwner(UUID id, String keycloakSubject) {
        return users.findByKeycloakSubject(keycloakSubject)
                .map(user -> user.getId().equals(id))
                .orElse(false);
    }

    private UserAccount load(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", 404));
    }

    private UserAccount provision(PayflowPrincipal principal) {
        String email = requiredEmail(principal);
        UserAccount user = users.findByEmail(email).orElseGet(UserAccount::new);
        boolean isNew = user.getId() == null;
        if (isNew) {
            user.setId(subjectUuid(principal.userId()));
            user.setKycStatus(KycStatus.PENDING);
            user.setCreatedAt(Instant.now());
        }
        user.setKeycloakSubject(principal.userId());
        synchronizeClaims(user, principal);
        users.save(user);
        if (isNew) {
        outbox.add(Topics.USER_REGISTERED, user.getId().toString(),
                    new UserRegisteredEvent(UUID.randomUUID(), user.getId(), user.getEmail(),
                            user.getCountry(), Instant.now()));
        }
        return user;
    }

    private void synchronizeClaims(UserAccount user, PayflowPrincipal principal) {
        user.setEmail(requiredEmail(principal));
        user.setFullName(valueOrDefault(principal.fullName(), user.getFullName(), principal.email()));
        user.setCountry(valueOrDefault(principal.country(), user.getCountry(), "US").toUpperCase());
        user.setRole(principal.roles().contains("ADMIN") ? Role.ADMIN : Role.USER);
        user.setEnabled(true);
        user.setUpdatedAt(Instant.now());
    }

    private String requiredEmail(PayflowPrincipal principal) {
        if (principal.email() == null || principal.email().isBlank()) {
            throw new BusinessException("EMAIL_CLAIM_REQUIRED", "The identity token must contain an email", 400);
        }
        return principal.email().toLowerCase();
    }

    private String valueOrDefault(String preferred, String existing, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return existing != null && !existing.isBlank() ? existing : fallback;
    }

    private UUID subjectUuid(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(subject.getBytes(StandardCharsets.UTF_8));
        }
    }

    private UserProfileResponse toProfile(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getCountry(), user.getRole(), user.getKycStatus(), user.getCreatedAt());
    }
}
