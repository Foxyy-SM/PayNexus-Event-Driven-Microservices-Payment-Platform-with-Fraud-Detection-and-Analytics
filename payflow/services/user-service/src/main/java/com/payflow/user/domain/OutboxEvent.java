package com.payflow.user.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="outbox_events")
public class OutboxEvent {
 @Id private UUID id; @Column(nullable=false) private String topic; @Column(name="event_key",nullable=false) private String eventKey;
 @Column(nullable=false) private String eventType; @Column(nullable=false,columnDefinition="TEXT") private String payload;
 @Column(nullable=false) private Instant createdAt; private Instant publishedAt; @Column(nullable=false) private int attempts;
 @Column(nullable=false) private Instant nextAttemptAt; private String lastError;
 public UUID getId(){return id;}public void setId(UUID v){id=v;}public String getTopic(){return topic;}public void setTopic(String v){topic=v;}
 public String getEventKey(){return eventKey;}public void setEventKey(String v){eventKey=v;}public String getEventType(){return eventType;}public void setEventType(String v){eventType=v;}
 public String getPayload(){return payload;}public void setPayload(String v){payload=v;}public Instant getCreatedAt(){return createdAt;}public void setCreatedAt(Instant v){createdAt=v;}
 public Instant getPublishedAt(){return publishedAt;}public void setPublishedAt(Instant v){publishedAt=v;}public int getAttempts(){return attempts;}public void setAttempts(int v){attempts=v;}
 public Instant getNextAttemptAt(){return nextAttemptAt;}public void setNextAttemptAt(Instant v){nextAttemptAt=v;}public String getLastError(){return lastError;}public void setLastError(String v){lastError=v;}
}
