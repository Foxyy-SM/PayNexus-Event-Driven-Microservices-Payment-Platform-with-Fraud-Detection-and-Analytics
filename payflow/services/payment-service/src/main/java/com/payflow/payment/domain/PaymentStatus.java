package com.payflow.payment.domain;

public enum PaymentStatus {
    RESERVED,
    PENDING_REVIEW,
    CAPTURING,
    COMPLETED,
    REJECTED,
    FAILED
}
