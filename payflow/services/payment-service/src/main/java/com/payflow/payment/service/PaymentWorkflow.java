package com.payflow.payment.service;
import com.payflow.common.exception.BusinessException;
import com.payflow.common.security.PayflowPrincipal;
import com.payflow.events.*;
import com.payflow.payment.client.*;
import com.payflow.payment.domain.*;
import com.payflow.payment.dto.*;
import com.payflow.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class PaymentWorkflow {
 private final PaymentRepository repo;private final IdempotencyService idem;private final WalletClient wallet;
 private final FraudClient fraud;private final OutboxService outbox;private final MeterRegistry metrics;
 public PaymentWorkflow(PaymentRepository r,IdempotencyService i,WalletClient w,FraudClient f,OutboxService o,MeterRegistry m){
  repo=r;idem=i;wallet=w;fraud=f;outbox=o;metrics=m;
 }
 @Transactional public PaymentResponse create(PayflowPrincipal principal,String key,CreatePaymentRequest req){
  idem.requireKey(key);UUID user=UUID.fromString(principal.userId());
  var old=repo.findByUserIdAndIdempotencyKey(user,key);if(old.isPresent())return view(old.get());
  if(!idem.acquireLock(user.toString(),key))return awaitExisting(user,key);
  Payment p=new Payment();p.setId(UUID.randomUUID());p.setUserId(user);p.setAmountMinor(req.amountMinor());
  p.setCurrency(req.currency().toUpperCase());p.setMerchantId(req.merchantId());p.setCountry(req.country()==null?"IN":req.country().toUpperCase());
  p.setIdempotencyKey(key);p.setCreatedAt(Instant.now());p.setUpdatedAt(Instant.now());
  p.setStatus(PaymentStatus.RESERVED);repo.saveAndFlush(p);idem.rememberPayment(user.toString(),key,p.getId());
  wallet.reserve(user,p.getAmountMinor(),p.getId());
  outbox.add(Topics.PAYMENT_INITIATED,p.getId().toString(),new PaymentInitiatedEvent(UUID.randomUUID(),p.getId(),user,
   p.getAmountMinor(),p.getCurrency(),p.getMerchantId(),p.getCountry(),key,Instant.now()));
  outbox.add(Topics.PAYMENT_RESERVED,p.getId().toString(),state(p));
  try{
   var score=fraud.score(p.getId(),user,p.getAmountMinor(),p.getCountry(),p.getMerchantId());
   p.setRiskScore(score.riskScore());p.setFraudDecision(score.decision());
   if("REVIEW".equalsIgnoreCase(score.decision())){p.setStatus(PaymentStatus.PENDING_REVIEW);repo.save(p);
    outbox.add(Topics.PAYMENT_PENDING_REVIEW,p.getId().toString(),state(p));metrics.counter("payflow.payments.pending_review").increment();return view(p);}
   if("REJECT".equalsIgnoreCase(score.decision()))return reject0(p,"FRAUD_REJECTED");
   return capture(p,principal.email());
  }catch(RuntimeException ex){try{wallet.release(user,p.getAmountMinor(),p.getId());}catch(RuntimeException ignored){}
   return fail(p,ex.getMessage()==null?"PROCESSING_ERROR":ex.getMessage());}
 }
 private PaymentResponse awaitExisting(UUID user,String key){
  for(int attempt=0;attempt<200;attempt++){
   UUID paymentId=idem.lookup(user.toString(),key);
   if(paymentId!=null){var payment=repo.findById(paymentId);if(payment.isPresent())return view(payment.get());}
   var payment=repo.findByUserIdAndIdempotencyKey(user,key);if(payment.isPresent())return view(payment.get());
   try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();
    throw new BusinessException("IDEMPOTENCY_REQUEST_INTERRUPTED","Interrupted while waiting for the original request",409);}
  }
  throw new BusinessException("IDEMPOTENCY_REQUEST_IN_PROGRESS","A request with this idempotency key is still processing",409);
 }
 @Transactional public PaymentResponse approve(UUID id){Payment p=require(id);if(p.getStatus()==PaymentStatus.COMPLETED)return view(p);review(p);return capture(p,null);}
 @Transactional public PaymentResponse reject(UUID id,String reason){Payment p=require(id);if(p.getStatus()==PaymentStatus.REJECTED)return view(p);
  review(p);return reject0(p,reason==null||reason.isBlank()?"ADMIN_REJECTED":reason);}
 public List<PaymentResponse> reviewQueue(){return repo.findByStatusOrderByCreatedAtAsc(PaymentStatus.PENDING_REVIEW).stream().map(this::view).toList();}
 private PaymentResponse capture(Payment p,String email){p.setStatus(PaymentStatus.CAPTURING);repo.save(p);wallet.capture(p.getUserId(),p.getAmountMinor(),p.getId());
  p.setStatus(PaymentStatus.COMPLETED);p.setCompletedAt(Instant.now());p.setUpdatedAt(Instant.now());repo.save(p);
  outbox.add(Topics.PAYMENT_COMPLETED,p.getId().toString(),new PaymentCompletedEvent(UUID.randomUUID(),p.getId(),p.getUserId(),null,
   p.getAmountMinor(),p.getCurrency(),p.getMerchantId(),Instant.now()));
  if(email!=null)outbox.add(Topics.NOTIFICATION_SEND,p.getUserId().toString(),new NotificationRequestedEvent(UUID.randomUUID(),p.getUserId(),
   "EMAIL","PAYMENT_COMPLETED",email,"{\"paymentId\":\""+p.getId()+"\"}",Instant.now()));
  metrics.counter("payflow.payments.completed").increment();return view(p);}
 private PaymentResponse reject0(Payment p,String reason){wallet.release(p.getUserId(),p.getAmountMinor(),p.getId());p.setStatus(PaymentStatus.REJECTED);
  p.setFailureReason(reason);repo.save(p);failedEvent(p,reason);metrics.counter("payflow.payments.rejected").increment();return view(p);}
 private PaymentResponse fail(Payment p,String reason){p.setStatus(PaymentStatus.FAILED);p.setFailureReason(reason);repo.save(p);failedEvent(p,reason);
  metrics.counter("payflow.payments.failed").increment();return view(p);}
 private void failedEvent(Payment p,String reason){outbox.add(Topics.PAYMENT_FAILED,p.getId().toString(),new PaymentFailedEvent(UUID.randomUUID(),p.getId(),
  p.getUserId(),reason,p.getStatus().name(),Instant.now()));}
 private Payment require(UUID id){return repo.findById(id).orElseThrow(()->new BusinessException("PAYMENT_NOT_FOUND","Payment not found",404));}
 private void review(Payment p){if(p.getStatus()!=PaymentStatus.PENDING_REVIEW)throw new BusinessException("INVALID_PAYMENT_STATE","Payment is not pending review",409);}
 public PaymentResponse get(UUID id,PayflowPrincipal principal){Payment p=require(id);if(!p.getUserId().toString().equals(principal.userId())&&
  (principal.roles()==null||!principal.roles().contains("ADMIN")))throw new BusinessException("FORBIDDEN","Not allowed to view this payment",403);return view(p);}
 public List<PaymentResponse> listMine(PayflowPrincipal principal){return repo.findByUserIdOrderByCreatedAtDesc(UUID.fromString(principal.userId())).stream().map(this::view).toList();}
 private PaymentStateEvent state(Payment p){return new PaymentStateEvent(UUID.randomUUID(),p.getId(),p.getUserId(),p.getAmountMinor(),p.getCurrency(),p.getStatus().name(),Instant.now());}
 private PaymentResponse view(Payment p){return new PaymentResponse(p.getId(),p.getUserId(),p.getAmountMinor(),p.getCurrency(),p.getMerchantId(),p.getStatus(),
  p.getRiskScore(),p.getFraudDecision(),p.getFailureReason(),p.getCreatedAt(),p.getCompletedAt());}
}
