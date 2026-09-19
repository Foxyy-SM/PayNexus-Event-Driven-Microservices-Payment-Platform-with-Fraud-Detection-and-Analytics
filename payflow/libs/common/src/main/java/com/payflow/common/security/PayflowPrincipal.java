package com.payflow.common.security;

public record PayflowPrincipal(String userId, String email, String roles, String fullName, String country) {
    public PayflowPrincipal(String userId, String email, String roles) {
        this(userId, email, roles, null, null);
    }
}
