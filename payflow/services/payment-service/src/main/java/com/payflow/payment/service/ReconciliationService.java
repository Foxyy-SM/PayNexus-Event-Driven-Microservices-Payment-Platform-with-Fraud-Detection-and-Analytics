package com.payflow.payment.service;

import com.payflow.payment.client.WalletClient;
import com.payflow.payment.domain.*;
import com.payflow.payment.dto.*;
import com.payflow.payment.repository.*;
import com.payflow.events.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class ReconciliationService {
    private final PaymentRepository payments;
    private final ReconciliationRunRepository runs;
    private final ReconciliationMismatchRepository mismatches;
    private final WalletClient wallet;
    private final OutboxService outbox;
    private final MeterRegistry metrics;
    private final Duration staleThreshold;
    private final Clock clock;

    public ReconciliationService(PaymentRepository payments, ReconciliationRunRepository runs,
            ReconciliationMismatchRepository mismatches, WalletClient wallet, OutboxService outbox, MeterRegistry metrics,
            @Value("${payflow.reconciliation.stale-threshold:PT30M}") Duration staleThreshold) {
        this(payments, runs, mismatches, wallet, outbox, metrics, staleThreshold, Clock.systemUTC());
    }

    ReconciliationService(PaymentRepository payments, ReconciliationRunRepository runs,
            ReconciliationMismatchRepository mismatches, WalletClient wallet, OutboxService outbox, MeterRegistry metrics,
            Duration staleThreshold, Clock clock) {
        this.payments = payments;
        this.runs = runs;
        this.mismatches = mismatches;
        this.wallet = wallet;
        this.outbox = outbox;
        this.metrics = metrics;
        this.staleThreshold = staleThreshold;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payflow.reconciliation.interval:PT5M}")
    public void scheduled() {
        run("SCHEDULED");
    }

    @Transactional
    public synchronized ReconciliationRunResponse run(String source) {
        Timer.Sample sample = Timer.start(metrics);
        ReconciliationRun run = new ReconciliationRun();
        run.setId(UUID.randomUUID());
        run.setTriggerSource(source);
        run.setStatus(ReconciliationRun.Status.RUNNING);
        run.setStaleThresholdSeconds(staleThreshold.toSeconds());
        run.setStartedAt(clock.instant());
        runs.saveAndFlush(run);
        metrics.counter("payflow.reconciliation.runs", "source", source).increment();
        try {
            List<Payment> candidates = payments.findAll();
            run.setPaymentsScanned(candidates.size());
            for (Payment payment : candidates) {
                inspect(run, payment);
            }
            run.setStatus(ReconciliationRun.Status.COMPLETED);
        } catch (RuntimeException exception) {
            run.setStatus(ReconciliationRun.Status.FAILED);
            run.setErrorMessage(limit(exception.getMessage()));
            metrics.counter("payflow.reconciliation.run_failures").increment();
        } finally {
            run.setCompletedAt(clock.instant());
            runs.save(run);
            sample.stop(metrics.timer("payflow.reconciliation.duration", "source", source));
        }
        return view(run);
    }

    public List<ReconciliationRunResponse> listRuns() {
        return runs.findAllByOrderByStartedAtDesc().stream().map(this::view).toList();
    }

    public List<ReconciliationMismatchResponse> listMismatches(UUID runId) {
        return mismatches.findByRunIdOrderByDetectedAtAsc(runId).stream().map(this::view).toList();
    }

    public List<ReconciliationMismatchResponse> listMismatches() {
        return mismatches.findAllByOrderByDetectedAtDesc().stream().map(this::view).toList();
    }

    private void inspect(ReconciliationRun run, Payment payment) {
        List<WalletLedgerEntryView> phases;
        try {
            phases = wallet.phases(payment.getId());
        } catch (RuntimeException exception) {
            record(run, payment, ReconciliationMismatch.Type.REVIEW_PHASE_INVALID,
                    "wallet ledger phases available", "wallet lookup failed: " + limit(exception.getMessage()),
                    ReconciliationMismatch.Action.NONE, null);
            return;
        }
        boolean reserve = matching(phases, "RESERVE", payment.getAmountMinor());
        boolean anyReserve = has(phases, "RESERVE");
        boolean capture = matching(phases, "CAPTURE", payment.getAmountMinor());
        boolean release = matching(phases, "RELEASE", payment.getAmountMinor());
        boolean anyCapture = has(phases, "CAPTURE");
        boolean anyRelease = has(phases, "RELEASE");
        String actual = evidence(phases);

        if (payment.getStatus() == PaymentStatus.COMPLETED && !capture) {
            record(run, payment, ReconciliationMismatch.Type.COMPLETED_CAPTURE_MISSING_OR_WRONG,
                    "CAPTURE amountMinor=" + payment.getAmountMinor(), actual,
                    ReconciliationMismatch.Action.NONE, null);
        }
        if ((payment.getStatus() == PaymentStatus.REJECTED || payment.getStatus() == PaymentStatus.FAILED)
                && anyReserve && !release) {
            boolean safe = reserve && !anyCapture && !anyRelease;
            record(run, payment, ReconciliationMismatch.Type.TERMINAL_RELEASE_MISSING,
                    "RELEASE amountMinor=" + payment.getAmountMinor(), actual,
                    safe ? ReconciliationMismatch.Action.RELEASE : ReconciliationMismatch.Action.NONE,
                    safe ? () -> wallet.release(payment.getUserId(), payment.getAmountMinor(), payment.getId()) : null);
        }
        if (payment.getStatus() == PaymentStatus.PENDING_REVIEW) {
            boolean valid = reserve && !anyCapture && !anyRelease;
            if (!valid) {
                record(run, payment, ReconciliationMismatch.Type.REVIEW_PHASE_INVALID,
                        "matching RESERVE without CAPTURE or RELEASE", actual,
                        ReconciliationMismatch.Action.NONE, null);
            }
            if (stale(payment)) {
                record(run, payment, ReconciliationMismatch.Type.STALE_PENDING_REVIEW,
                        "review younger than " + staleThreshold, actual, ReconciliationMismatch.Action.RELEASE, () -> {
                            wallet.release(payment.getUserId(), payment.getAmountMinor(), payment.getId());
                            payment.setStatus(PaymentStatus.FAILED);
                            payment.setFailureReason("STALE_REVIEW_RELEASED_BY_RECONCILIATION");
                            payment.setUpdatedAt(clock.instant());
                            payments.save(payment);
                            outbox.add(Topics.PAYMENT_FAILED, payment.getId().toString(), new PaymentFailedEvent(
                                    UUID.randomUUID(), payment.getId(), payment.getUserId(), payment.getFailureReason(),
                                    payment.getStatus().name(), clock.instant()));
                            metrics.counter("payflow.payments.failed").increment();
                        }, valid);
            }
        }
        if (payment.getStatus() == PaymentStatus.CAPTURING && stale(payment)) {
            boolean safe = reserve && !anyCapture && !anyRelease;
            record(run, payment, ReconciliationMismatch.Type.STALE_CAPTURING,
                    "capturing younger than " + staleThreshold, actual,
                    safe ? ReconciliationMismatch.Action.CAPTURE : ReconciliationMismatch.Action.NONE,
                    safe ? () -> {
                        wallet.capture(payment.getUserId(), payment.getAmountMinor(), payment.getId());
                        payment.setStatus(PaymentStatus.COMPLETED);
                        payment.setCompletedAt(clock.instant());
                        payment.setUpdatedAt(clock.instant());
                        payments.save(payment);
                        outbox.add(Topics.PAYMENT_COMPLETED, payment.getId().toString(), new PaymentCompletedEvent(
                                UUID.randomUUID(), payment.getId(), payment.getUserId(), null,
                                payment.getAmountMinor(), payment.getCurrency(), payment.getMerchantId(), clock.instant()));
                        metrics.counter("payflow.payments.completed").increment();
                    } : null);
        }
    }

    private void record(ReconciliationRun run, Payment payment, ReconciliationMismatch.Type type,
            String expected, String actual, ReconciliationMismatch.Action action, Runnable repair, boolean repairAllowed) {
        record(run, payment, type, expected, actual,
                repairAllowed ? action : ReconciliationMismatch.Action.NONE, repairAllowed ? repair : null);
    }

    private void record(ReconciliationRun run, Payment payment, ReconciliationMismatch.Type type,
            String expected, String actual, ReconciliationMismatch.Action action, Runnable repair) {
        ReconciliationMismatch mismatch = new ReconciliationMismatch();
        mismatch.setId(UUID.randomUUID());
        mismatch.setRunId(run.getId());
        mismatch.setPaymentId(payment.getId());
        mismatch.setMismatchType(type);
        mismatch.setPaymentStatus(payment.getStatus());
        mismatch.setExpectedEvidence(expected);
        mismatch.setActualEvidence(actual);
        mismatch.setRepairAction(action);
        mismatch.setDetectedAt(clock.instant());
        mismatch.setRepairStatus(repair == null ? ReconciliationMismatch.RepairStatus.NOT_APPLICABLE
                : ReconciliationMismatch.RepairStatus.PENDING);
        mismatches.saveAndFlush(mismatch);
        run.setMismatchesFound(run.getMismatchesFound() + 1);
        metrics.counter("payflow.reconciliation.mismatches", "type", type.name()).increment();
        if (repair == null) return;
        try {
            repair.run();
            mismatch.setRepairStatus(ReconciliationMismatch.RepairStatus.SUCCEEDED);
            mismatch.setRepairDetail("Idempotent wallet operation and payment state update completed");
            mismatch.setRepairedAt(clock.instant());
            run.setRepairsSucceeded(run.getRepairsSucceeded() + 1);
            metrics.counter("payflow.reconciliation.repairs", "action", action.name(), "outcome", "succeeded").increment();
        } catch (RuntimeException exception) {
            mismatch.setRepairStatus(ReconciliationMismatch.RepairStatus.FAILED);
            mismatch.setRepairDetail(limit(exception.getMessage()));
            run.setRepairsFailed(run.getRepairsFailed() + 1);
            metrics.counter("payflow.reconciliation.repairs", "action", action.name(), "outcome", "failed").increment();
        }
        mismatches.save(mismatch);
    }

    private boolean stale(Payment payment) {
        Instant changed = payment.getUpdatedAt() == null ? payment.getCreatedAt() : payment.getUpdatedAt();
        return changed != null && !changed.isAfter(clock.instant().minus(staleThreshold));
    }

    private static boolean matching(List<WalletLedgerEntryView> phases, String type, long amount) {
        return phases.stream().anyMatch(p -> type.equals(p.entryType()) && p.amountMinor() == amount);
    }
    private static boolean has(List<WalletLedgerEntryView> phases, String type) {
        return phases.stream().anyMatch(p -> type.equals(p.entryType()));
    }
    private static String evidence(List<WalletLedgerEntryView> phases) {
        if (phases.isEmpty()) return "no ledger phases";
        return phases.stream().map(p -> p.entryType() + ":" + p.amountMinor()).toList().toString();
    }
    private static String limit(String value) {
        if (value == null) return "unspecified error";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
    private ReconciliationRunResponse view(ReconciliationRun r) {
        return new ReconciliationRunResponse(r.getId(), r.getTriggerSource(), r.getStatus(),
                r.getStaleThresholdSeconds(), r.getStartedAt(), r.getCompletedAt(), r.getPaymentsScanned(),
                r.getMismatchesFound(), r.getRepairsSucceeded(), r.getRepairsFailed(), r.getErrorMessage());
    }
    private ReconciliationMismatchResponse view(ReconciliationMismatch m) {
        return new ReconciliationMismatchResponse(m.getId(), m.getRunId(), m.getPaymentId(),
                m.getMismatchType(), m.getPaymentStatus(), m.getExpectedEvidence(), m.getActualEvidence(),
                m.getRepairAction(), m.getRepairStatus(), m.getRepairDetail(), m.getDetectedAt(), m.getRepairedAt());
    }
}
