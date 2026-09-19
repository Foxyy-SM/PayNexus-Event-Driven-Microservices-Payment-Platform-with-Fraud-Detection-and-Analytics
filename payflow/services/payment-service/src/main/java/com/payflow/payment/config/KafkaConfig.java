package com.payflow.payment.config;

import com.payflow.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean
    NewTopic paymentInitiated() {
        return TopicBuilder.name(Topics.PAYMENT_INITIATED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentCompleted() {
        return TopicBuilder.name(Topics.PAYMENT_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentFailed() {
        return TopicBuilder.name(Topics.PAYMENT_FAILED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic notifications() {
        return TopicBuilder.name(Topics.NOTIFICATION_SEND).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic notificationDlt() {
        return TopicBuilder.name(Topics.NOTIFICATION_DLT).partitions(3).replicas(1).build();
    }
}
