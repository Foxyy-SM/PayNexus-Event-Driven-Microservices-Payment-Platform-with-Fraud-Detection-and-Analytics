package com.payflow.fraud.config;

import com.payflow.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean
    NewTopic fraudChecked() {
        return TopicBuilder.name(Topics.FRAUD_CHECKED).partitions(3).replicas(1).build();
    }
}
