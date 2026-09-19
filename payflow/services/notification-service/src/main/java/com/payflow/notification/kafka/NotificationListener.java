package com.payflow.notification.kafka;

import com.payflow.events.NotificationRequestedEvent;
import com.payflow.events.Topics;
import com.payflow.notification.service.NotificationDispatcher;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {
    private final NotificationDispatcher dispatcher;

    public NotificationListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500, multiplier = 2),
            dltTopicSuffix = ".DLT",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = Topics.NOTIFICATION_SEND, groupId = "notification-service")
    public void onNotification(NotificationRequestedEvent event) {
        dispatcher.dispatch(event);
    }

    @DltHandler
    public void onDlt(NotificationRequestedEvent event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        org.slf4j.LoggerFactory.getLogger(getClass())
                .error("Notification landed on DLT topic={} user={}", topic, event.userId());
    }
}
