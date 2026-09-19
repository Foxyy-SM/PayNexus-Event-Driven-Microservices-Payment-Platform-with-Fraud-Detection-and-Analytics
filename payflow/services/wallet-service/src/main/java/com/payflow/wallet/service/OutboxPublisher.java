package com.payflow.wallet.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.wallet.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
@Component
public class OutboxPublisher {
    private final OutboxEventRepository repository; private final ObjectMapper mapper;
    private final KafkaOperations<String,Object> kafka; private final MeterRegistry metrics;
    public OutboxPublisher(OutboxEventRepository r,ObjectMapper m,KafkaOperations<String,Object> k,MeterRegistry x){
        repository=r;mapper=m;kafka=k;metrics=x;
    }
    @Scheduled(fixedDelayString="${payflow.outbox.poll-ms:500}") @Transactional
    public void publish(){
        for(var row:repository.findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAt(Instant.now(),PageRequest.of(0,100))){
            try{
                Object event=mapper.readValue(row.getPayload(),Class.forName(row.getEventType()));
                kafka.send(row.getTopic(),row.getEventKey(),event).get(10, TimeUnit.SECONDS);
                row.setPublishedAt(Instant.now());metrics.counter("payflow.outbox.published").increment();
            }catch(Exception ex){
                row.setAttempts(row.getAttempts()+1);
                row.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(Math.min(300,1L<<Math.min(row.getAttempts(),8)))));
                String message=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                row.setLastError(message.substring(0,Math.min(500,message.length())));
                metrics.counter("payflow.outbox.failures").increment();
            }
            repository.save(row);
        }
    }
}
