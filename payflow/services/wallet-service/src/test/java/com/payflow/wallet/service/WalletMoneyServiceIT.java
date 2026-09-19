package com.payflow.wallet.service;

import com.payflow.common.exception.BusinessException;
import com.payflow.wallet.domain.LedgerEntryType;
import com.payflow.wallet.repository.LedgerEntryRepository;
import com.payflow.wallet.repository.OutboxEventRepository;
import com.payflow.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused"
})
@Testcontainers(disabledWithoutDocker = true)
class WalletMoneyServiceIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired WalletMoneyService service;
    @Autowired WalletRepository wallets;
    @Autowired LedgerEntryRepository ledger;
    @Autowired OutboxEventRepository outbox;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        ledger.deleteAll();
        wallets.deleteAll();
    }

    @Test
    void concurrentCompetingReservesNeverOverspendAndBalancesMatchLedger() throws Exception {
        UUID user = UUID.randomUUID();
        service.openWallet(user, "INR");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> reserve = () -> {
            ready.countDown();
            start.await();
            try {
                service.reserve(user, 6_000_000, UUID.randomUUID());
                return true;
            } catch (BusinessException exception) {
                return false;
            }
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(reserve);
            var second = pool.submit(reserve);
            ready.await();
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }

        var stored = service.getByUser(user);
        var entries = service.history(user);
        long availableFromLedger = entries.stream().mapToLong(entry -> switch (entry.entryType()) {
            case CREDIT, RELEASE -> entry.amountMinor();
            case RESERVE -> -entry.amountMinor();
            case CAPTURE -> 0;
        }).sum();
        long reservedFromLedger = entries.stream().mapToLong(entry -> switch (entry.entryType()) {
            case RESERVE -> entry.amountMinor();
            case CAPTURE, RELEASE -> -entry.amountMinor();
            case CREDIT -> 0;
        }).sum();

        assertThat(stored.availableBalanceMinor()).isEqualTo(4_000_000).isEqualTo(availableFromLedger);
        assertThat(stored.reservedBalanceMinor()).isEqualTo(6_000_000).isEqualTo(reservedFromLedger);
        assertThat(entries).extracting(entry -> entry.entryType())
                .containsExactlyInAnyOrder(LedgerEntryType.CREDIT, LedgerEntryType.RESERVE);
    }

    @Test
    void everyMoneyPhaseIsIdempotent() {
        UUID user = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        service.openWallet(user, "INR");

        var reserved = service.reserve(user, 750, payment);
        assertThat(service.reserve(user, 750, payment)).isEqualTo(reserved);
        var captured = service.capture(user, 750, payment);
        assertThat(service.capture(user, 750, payment)).isEqualTo(captured);

        assertThat(service.phases(payment)).extracting(entry -> entry.entryType())
                .containsExactly(LedgerEntryType.RESERVE, LedgerEntryType.CAPTURE);
        assertThat(service.getByUser(user).availableBalanceMinor()).isEqualTo(9_999_250);
        assertThat(service.getByUser(user).reservedBalanceMinor()).isZero();
    }
}
