package com.payflow.payment.service;
import com.payflow.common.security.PayflowPrincipal;
import com.payflow.common.security.ServiceTokenProvider;
import com.payflow.payment.client.*;
import com.payflow.payment.domain.*;
import com.payflow.payment.dto.*;
import com.payflow.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import java.net.SocketTimeoutException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MoneyWorkflowTest {
 @Mock PaymentRepository repo;@Mock IdempotencyService idem;@Mock WalletClient wallet;@Mock FraudClient fraud;@Mock OutboxService outbox;
 PaymentWorkflow flow;
 @BeforeEach void setup(){flow=new PaymentWorkflow(repo,idem,wallet,fraud,outbox,new SimpleMeterRegistry());
  lenient().when(repo.save(any())).thenAnswer(i->i.getArgument(0));lenient().when(repo.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
  lenient().when(idem.acquireLock(anyString(),anyString())).thenReturn(true);}
 @Test void approveReservesThenCaptures(){UUID user=UUID.randomUUID();when(repo.findByUserIdAndIdempotencyKey(user,"key")).thenReturn(Optional.empty());
  when(wallet.reserve(any(),anyLong(),any())).thenReturn(new WalletView(UUID.randomUUID(),user,1000,100,"INR",0L));
  when(fraud.score(any(),any(),anyLong(),any(),any())).thenReturn(new FraudScoreResponse(.1,"APPROVE","ok"));
  var result=flow.create(new PayflowPrincipal(user.toString(),"a@b.com","USER"),"key",new CreatePaymentRequest(100L,"INR","shop","IN"));
  assertEquals(PaymentStatus.COMPLETED,result.status());verify(wallet).reserve(any(),eq(100L),any());verify(wallet).capture(any(),eq(100L),any());}
 @Test void reviewHoldsReservation(){UUID user=UUID.randomUUID();when(repo.findByUserIdAndIdempotencyKey(user,"review")).thenReturn(Optional.empty());
  when(wallet.reserve(any(),anyLong(),any())).thenReturn(new WalletView(UUID.randomUUID(),user,1000,100,"INR",0L));
  when(fraud.score(any(),any(),anyLong(),any(),any())).thenReturn(new FraudScoreResponse(.5,"REVIEW","review"));
  var result=flow.create(new PayflowPrincipal(user.toString(),"a@b.com","USER"),"review",new CreatePaymentRequest(100L,"INR","shop","IN"));
  assertEquals(PaymentStatus.PENDING_REVIEW,result.status());verify(wallet,never()).capture(any(),anyLong(),any());}
 @Test void walletReserveTimeoutFailsFastWithoutAnotherMoneyMove(){UUID user=UUID.randomUUID();
  when(repo.findByUserIdAndIdempotencyKey(user,"timeout")).thenReturn(Optional.empty());
  when(wallet.reserve(any(),anyLong(),any())).thenThrow(new ResourceAccessException("timed out",new SocketTimeoutException()));
  assertThrows(ResourceAccessException.class,()->flow.create(new PayflowPrincipal(user.toString(),"a@b.com","USER"),"timeout",
   new CreatePaymentRequest(100L,"INR","shop","IN")));
  verify(wallet,times(1)).reserve(any(),eq(100L),any());verify(wallet,never()).capture(any(),anyLong(),any());
  verify(wallet,never()).release(any(),anyLong(),any());verifyNoInteractions(fraud);}
 @Test void fraudFallbackProducesHeldPendingReview(){UUID user=UUID.randomUUID();when(repo.findByUserIdAndIdempotencyKey(user,"degraded")).thenReturn(Optional.empty());
  when(wallet.reserve(any(),anyLong(),any())).thenReturn(new WalletView(UUID.randomUUID(),user,1000,100,"INR",0L));
  FraudScoreResponse fallback=fraudFallback();
  when(fraud.score(any(),any(),anyLong(),any(),any())).thenReturn(fallback);
  var result=flow.create(new PayflowPrincipal(user.toString(),"a@b.com","USER"),"degraded",new CreatePaymentRequest(100L,"INR","shop","IN"));
  assertEquals(PaymentStatus.PENDING_REVIEW,result.status());assertEquals("REVIEW",result.fraudDecision());
  verify(wallet,never()).capture(any(),anyLong(),any());verify(wallet,never()).release(any(),anyLong(),any());}
 @Test void sameKeyIsScopedToDifferentUsers(){UUID first=UUID.randomUUID(),second=UUID.randomUUID();
  when(repo.findByUserIdAndIdempotencyKey(any(),eq("shared"))).thenReturn(Optional.empty());
  when(wallet.reserve(any(),anyLong(),any())).thenReturn(new WalletView(UUID.randomUUID(),first,1000,100,"INR",0L));
  when(fraud.score(any(),any(),anyLong(),any(),any())).thenReturn(new FraudScoreResponse(.1,"APPROVE","ok"));
  var request=new CreatePaymentRequest(100L,"INR","shop","IN");
  var one=flow.create(new PayflowPrincipal(first.toString(),"one@b.com","USER"),"shared",request);
  var two=flow.create(new PayflowPrincipal(second.toString(),"two@b.com","USER"),"shared",request);
  assertEquals(first,one.userId());assertEquals(second,two.userId());verify(wallet,times(2)).reserve(any(),eq(100L),any());}
 @Test void adminCanApproveOrRejectReviewWithoutDoubleResolution(){Payment approve=reviewPayment(),reject=reviewPayment();
  when(repo.findById(approve.getId())).thenReturn(Optional.of(approve));when(repo.findById(reject.getId())).thenReturn(Optional.of(reject));
  assertEquals(PaymentStatus.COMPLETED,flow.approve(approve.getId()).status());
  assertEquals(PaymentStatus.REJECTED,flow.reject(reject.getId(),"MANUAL_REJECT").status());
  assertEquals(PaymentStatus.COMPLETED,flow.approve(approve.getId()).status());
  assertEquals(PaymentStatus.REJECTED,flow.reject(reject.getId(),null).status());
  verify(wallet,times(1)).capture(approve.getUserId(),approve.getAmountMinor(),approve.getId());
  verify(wallet,times(1)).release(reject.getUserId(),reject.getAmountMinor(),reject.getId());}
 private Payment reviewPayment(){Payment p=new Payment();p.setId(UUID.randomUUID());p.setUserId(UUID.randomUUID());p.setAmountMinor(100);
  p.setCurrency("INR");p.setMerchantId("shop");p.setCountry("IN");p.setStatus(PaymentStatus.PENDING_REVIEW);p.setCreatedAt(java.time.Instant.now());return p;}
 private FraudScoreResponse fraudFallback(){RestClient.Builder builder=mock(RestClient.Builder.class);
  when(builder.baseUrl(anyString())).thenReturn(builder);when(builder.build()).thenReturn(mock(RestClient.class));
  return new FraudClient(builder,mock(ServiceTokenProvider.class),"http://unused")
   .fallback(UUID.randomUUID(),UUID.randomUUID(),100L,"IN","shop",new SocketTimeoutException());}
}
