package com.payflow.user.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import com.payflow.events.Topics;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name(Topics.USER_REGISTERED).partitions(3).replicas(1).build();
    }
}
