package com.payflow.wallet.config;

import com.payflow.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic walletLedgerTopic() {
        return TopicBuilder.name(Topics.WALLET_LEDGER).partitions(3).replicas(1).build();
    }
}
