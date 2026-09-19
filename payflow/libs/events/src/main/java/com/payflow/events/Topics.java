package com.payflow.events;

public final class Topics {
    public static final String PAYMENT_INITIATED = "payment.initiated";
    public static final String PAYMENT_RESERVED = "payment.reserved";
    public static final String PAYMENT_PENDING_REVIEW = "payment.pending-review";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String FRAUD_CHECKED = "fraud.checked";
    public static final String USER_REGISTERED = "user.registered";
    public static final String NOTIFICATION_SEND = "notification.send";
    public static final String NOTIFICATION_DLT = "notification.send.DLT";
    public static final String WALLET_LEDGER = "wallet.ledger";

    private Topics() {
    }
}
