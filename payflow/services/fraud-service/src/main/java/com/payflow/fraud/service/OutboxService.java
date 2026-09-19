package com.payflow.fraud.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.fraud.domain.OutboxEvent;
import com.payflow.fraud.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
@Component
public class OutboxService {
 private final OutboxEventRepository repository; private final ObjectMapper mapper;
 public OutboxService(OutboxEventRepository r,ObjectMapper m){repository=r;mapper=m;}
 public void add(String topic,String key,Object event){OutboxEvent row=new OutboxEvent();row.setId(UUID.randomUUID());row.setTopic(topic);row.setEventKey(key);row.setEventType(event.getClass().getName());
  try{row.setPayload(mapper.writeValueAsString(event));}catch(Exception ex){throw new IllegalStateException(ex);}
  row.setCreatedAt(Instant.now());row.setNextAttemptAt(Instant.now());repository.save(row);}
}
