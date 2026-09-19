package com.payflow.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.security.PayflowPrincipal;
import com.payflow.events.PaymentStateEvent;
import com.payflow.payment.client.FraudClient;
import com.payflow.payment.client.WalletClient;
import com.payflow.payment.domain.OutboxEvent;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.FraudScoreResponse;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.repository.OutboxEventRepository;
import com.payflow.payment.repository.PaymentRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused",
        "payflow.outbox.poll-ms=3600000",
        "payflow.security.client.client-secret=test-secret"
})
@Testcontainers(disabledWithoutDocker = true)
class PaymentInfrastructureIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired PaymentWorkflow workflow;
    @Autowired PaymentRepository payments;
    @Autowired OutboxService outboxService;
    @Autowired OutboxPublisher publisher;
    @Autowired OutboxEventRepository outbox;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @MockBean WalletClient wallet;
    @MockBean FraudClient fraud;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
        payments.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        Mockito.reset(wallet, fraud);
        when(fraud.score(any(), any(), anyLong(), anyString(), anyString()))
                .thenReturn(new FraudScoreResponse(0.1, "APPROVE", "ok"));
    }

    @Test
    void sameIdempotencyKeyBelongsToEachUser() {
        var request = new CreatePaymentRequest(100L, "INR", "shop", "IN");
        var first = workflow.create(principal(UUID.randomUUID()), "shared", request);
        var second = workflow.create(principal(UUID.randomUUID()), "shared", request);

        assertThat(first.userId()).isNotEqualTo(second.userId());
        assertThat(payments.count()).isEqualTo(2);
        verify(wallet, times(2)).reserve(any(), anyLong(), any());
    }

    @Test
    void concurrentDuplicateRequestReturnsOnePersistedPaymentAndMovesMoneyOnce() throws Exception {
        UUID user = UUID.randomUUID();
        var principal = principal(user);
        var request = new CreatePaymentRequest(250L, "INR", "shop", "IN");
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch continueFirst = new CountDownLatch(1);
        when(wallet.reserve(any(), anyLong(), any())).thenAnswer(invocation -> {
            reserved.countDown();
            continueFirst.await();
            return null;
        });

        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<PaymentResponse> first = pool.submit(() -> workflow.create(principal, "duplicate", request));
            reserved.await();
            Future<PaymentResponse> second = pool.submit(() -> workflow.create(principal, "duplicate", request));
            continueFirst.countDown();

            assertThat(first.get().paymentId()).isEqualTo(second.get().paymentId());
        }

        assertThat(payments.count()).isOne();
        verify(wallet, times(1)).reserve(user, 250L, payments.findAll().getFirst().getId());
        verify(wallet, times(1)).capture(user, 250L, payments.findAll().getFirst().getId());
    }

    @Test
    void outboxPersistsPublishesAndRetriesThroughRealKafka() throws Exception {
        String topic = "payment-it-" + UUID.randomUUID();
        PaymentStateEvent event = event();
        outboxService.add(topic, event.paymentId().toString(), event);
        assertThat(outbox.count()).isOne();

        try (var consumer = consumer(topic)) {
            publisher.publish();
            assertThat(outbox.findAll().getFirst().getPublishedAt()).isNotNull();
            assertThat(consumer.poll(Duration.ofSeconds(10)).count()).isOne();
        }

        OutboxEvent retry = new OutboxEvent();
        retry.setId(UUID.randomUUID());
        retry.setTopic(topic);
        retry.setEventKey("retry");
        retry.setEventType("not.a.RealEvent");
        retry.setPayload("{}");
        retry.setCreatedAt(Instant.now());
        retry.setNextAttemptAt(Instant.EPOCH);
        outbox.saveAndFlush(retry);

        publisher.publish();
        retry = outbox.findById(retry.getId()).orElseThrow();
        assertThat(retry.getAttempts()).isOne();
        assertThat(retry.getPublishedAt()).isNull();
        assertThat(retry.getNextAttemptAt()).isAfter(Instant.now());

        retry.setEventType(PaymentStateEvent.class.getName());
        retry.setPayload(objectMapper.writeValueAsString(event()));
        retry.setNextAttemptAt(Instant.EPOCH);
        outbox.saveAndFlush(retry);
        publisher.publish();

        retry = outbox.findById(retry.getId()).orElseThrow();
        assertThat(retry.getPublishedAt()).isNotNull();
        assertThat(retry.getAttempts()).isOne();
    }

    private KafkaConsumer<String, byte[]> consumer(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        consumer.poll(Duration.ofMillis(500));
        return consumer;
    }

    private PayflowPrincipal principal(UUID user) {
        return new PayflowPrincipal(user.toString(), user + "@test.local", "USER");
    }

    private PaymentStateEvent event() {
        return new PaymentStateEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                100, "INR", "COMPLETED", Instant.now());
    }
}
