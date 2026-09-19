package com.payflow.payment.service;

import com.payflow.payment.client.WalletClient;
import com.payflow.payment.domain.*;
import com.payflow.payment.dto.WalletLedgerEntryView;
import com.payflow.payment.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {
    @Mock PaymentRepository payments;
    @Mock ReconciliationRunRepository runs;
    @Mock ReconciliationMismatchRepository mismatches;
    @Mock WalletClient wallet;
    @Mock OutboxService outbox;
    ReconciliationService service;
    Instant now = Instant.parse("2026-08-31T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(payments, runs, mismatches, wallet, outbox,
                new SimpleMeterRegistry(), Duration.ofMinutes(30), Clock.fixed(now, ZoneOffset.UTC));
        lenient().when(runs.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(runs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(mismatches.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(mismatches.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(payments.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void completedRequiresMatchingCaptureAmount() {
        Payment payment = payment(PaymentStatus.COMPLETED, now.minusSeconds(60), 500);
        when(payments.findAll()).thenReturn(List.of(payment));
        when(wallet.phases(payment.getId())).thenReturn(List.of(phase(payment, "CAPTURE", 499)));

        var result = service.run("ADMIN");

        assertEquals(1, result.mismatchesFound());
        assertEquals(0, result.repairsSucceeded());
        verify(wallet, never()).capture(any(), anyLong(), any());
    }

    @Test
    void failedReservationIsReleasedIdempotently() {
        Payment payment = payment(PaymentStatus.FAILED, now.minusSeconds(60), 500);
        when(payments.findAll()).thenReturn(List.of(payment));
        when(wallet.phases(payment.getId())).thenReturn(List.of(phase(payment, "RESERVE", 500)));

        var result = service.run("SCHEDULED");

        assertEquals(1, result.mismatchesFound());
        assertEquals(1, result.repairsSucceeded());
        verify(wallet).release(payment.getUserId(), 500, payment.getId());
    }

    @Test
    void staleCapturingWithUnambiguousReserveRetriesCapture() {
        Payment payment = payment(PaymentStatus.CAPTURING, now.minus(Duration.ofHours(1)), 500);
        when(payments.findAll()).thenReturn(List.of(payment));
        when(wallet.phases(payment.getId())).thenReturn(List.of(phase(payment, "RESERVE", 500)));

        var result = service.run("SCHEDULED");

        assertEquals(1, result.repairsSucceeded());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        verify(wallet).capture(payment.getUserId(), 500, payment.getId());
        verify(payments).save(payment);
    }

    @Test
    void pendingReviewRetainsReservationUntilStale() {
        Payment payment = payment(PaymentStatus.PENDING_REVIEW, now.minusSeconds(60), 500);
        when(payments.findAll()).thenReturn(List.of(payment));
        when(wallet.phases(payment.getId())).thenReturn(List.of(phase(payment, "RESERVE", 500)));

        var result = service.run("ADMIN");

        assertEquals(0, result.mismatchesFound());
        verify(wallet, never()).release(any(), anyLong(), any());
    }

    @Test
    void staleReviewReleasesReservationAndFailsPayment() {
        Payment payment = payment(PaymentStatus.PENDING_REVIEW, now.minus(Duration.ofHours(1)), 500);
        when(payments.findAll()).thenReturn(List.of(payment));
        when(wallet.phases(payment.getId())).thenReturn(List.of(phase(payment, "RESERVE", 500)));

        var result = service.run("SCHEDULED");

        assertEquals(1, result.repairsSucceeded());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(wallet).release(payment.getUserId(), 500, payment.getId());
        verify(outbox).add(eq(com.payflow.events.Topics.PAYMENT_FAILED), eq(payment.getId().toString()), any());
    }

    private Payment payment(PaymentStatus status, Instant updated, long amount) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmountMinor(amount);
        payment.setStatus(status);
        payment.setCreatedAt(updated);
        payment.setUpdatedAt(updated);
        return payment;
    }

    private WalletLedgerEntryView phase(Payment payment, String type, long amount) {
        return new WalletLedgerEntryView(UUID.randomUUID(), payment.getId(), type, amount, 1000, 500, now);
    }
}
