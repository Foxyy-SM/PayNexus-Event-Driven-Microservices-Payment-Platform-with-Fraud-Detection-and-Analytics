package com.payflow.wallet.service;
import com.payflow.common.exception.BusinessException;
import com.payflow.events.*;
import com.payflow.wallet.domain.*;
import com.payflow.wallet.dto.*;
import com.payflow.wallet.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
@Service
public class WalletMoneyService {
 private final WalletRepository wallets;private final LedgerEntryRepository ledger;private final OutboxService outbox;private final MeterRegistry metrics;
 public WalletMoneyService(WalletRepository w,LedgerEntryRepository l,OutboxService o,MeterRegistry m){wallets=w;ledger=l;outbox=o;metrics=m;}
 @Transactional public WalletResponse openWallet(UUID user,String currency){return wallets.findByUserId(user).map(this::view).orElseGet(()->{
  Wallet w=new Wallet();w.setId(UUID.randomUUID());w.setUserId(user);w.setAvailableBalanceMinor(10_000_000L);w.setReservedBalanceMinor(0);
  w.setCurrency(currency==null?"INR":currency);w.setCreatedAt(Instant.now());wallets.saveAndFlush(w);append(w,LedgerEntryType.CREDIT,10_000_000L,null);return view(w);});}
 public WalletResponse getByUser(UUID user){return view(require(user));}
 @Retryable(retryFor=ObjectOptimisticLockingFailureException.class,maxAttempts=5,backoff=@Backoff(delay=50,multiplier=2))
 @Transactional(isolation=Isolation.READ_COMMITTED) public WalletResponse reserve(UUID user,long amount,UUID payment){
  WalletResponse replay=replay(user,payment,LedgerEntryType.RESERVE,amount);if(replay!=null)return replay;Wallet w=locked(user);
  if(w.getAvailableBalanceMinor()<amount)throw new BusinessException("INSUFFICIENT_FUNDS","Available balance is too low",422);
  w.setAvailableBalanceMinor(Math.subtractExact(w.getAvailableBalanceMinor(),amount));w.setReservedBalanceMinor(Math.addExact(w.getReservedBalanceMinor(),amount));
  wallets.saveAndFlush(w);append(w,LedgerEntryType.RESERVE,amount,payment);return view(w);}
 @Retryable(retryFor=ObjectOptimisticLockingFailureException.class,maxAttempts=5,backoff=@Backoff(delay=50,multiplier=2))
 @Transactional(isolation=Isolation.READ_COMMITTED) public WalletResponse capture(UUID user,long amount,UUID payment){
  WalletResponse replay=replay(user,payment,LedgerEntryType.CAPTURE,amount);if(replay!=null)return replay;
  reservation(payment,amount,LedgerEntryType.RELEASE);Wallet w=locked(user);
  if(w.getReservedBalanceMinor()<amount)throw new BusinessException("RESERVATION_MISMATCH","Reserved balance is too low",409);
  w.setReservedBalanceMinor(Math.subtractExact(w.getReservedBalanceMinor(),amount));wallets.saveAndFlush(w);append(w,LedgerEntryType.CAPTURE,amount,payment);return view(w);}
 @Retryable(retryFor=ObjectOptimisticLockingFailureException.class,maxAttempts=5,backoff=@Backoff(delay=50,multiplier=2))
 @Transactional(isolation=Isolation.READ_COMMITTED) public WalletResponse release(UUID user,long amount,UUID payment){
  WalletResponse replay=replay(user,payment,LedgerEntryType.RELEASE,amount);if(replay!=null)return replay;
  reservation(payment,amount,LedgerEntryType.CAPTURE);Wallet w=locked(user);
  if(w.getReservedBalanceMinor()<amount)throw new BusinessException("RESERVATION_MISMATCH","Reserved balance is too low",409);
  w.setReservedBalanceMinor(Math.subtractExact(w.getReservedBalanceMinor(),amount));w.setAvailableBalanceMinor(Math.addExact(w.getAvailableBalanceMinor(),amount));
  wallets.saveAndFlush(w);append(w,LedgerEntryType.RELEASE,amount,payment);return view(w);}
 @Transactional public WalletResponse credit(UUID user,long amount,UUID payment){if(amount<=0)throw new BusinessException("INVALID_AMOUNT","Amount must be positive",400);
  Wallet w=locked(user);w.setAvailableBalanceMinor(Math.addExact(w.getAvailableBalanceMinor(),amount));wallets.saveAndFlush(w);append(w,LedgerEntryType.CREDIT,amount,payment);return view(w);}
 public List<LedgerResponse> history(UUID user){Wallet w=require(user);return ledger.findByWalletIdOrderByCreatedAtDesc(w.getId()).stream().map(e->
  new LedgerResponse(e.getId(),e.getPaymentId(),e.getEntryType(),e.getAmountMinor(),e.getAvailableBalanceAfterMinor(),e.getReservedBalanceAfterMinor(),e.getCreatedAt())).toList();}
 public List<LedgerResponse> phases(UUID payment){return ledger.findByPaymentIdOrderByCreatedAtAsc(payment).stream().map(e->
  new LedgerResponse(e.getId(),e.getPaymentId(),e.getEntryType(),e.getAmountMinor(),e.getAvailableBalanceAfterMinor(),e.getReservedBalanceAfterMinor(),e.getCreatedAt())).toList();}
 private void append(Wallet w,LedgerEntryType type,long amount,UUID payment){LedgerEntry e=new LedgerEntry();e.setId(UUID.randomUUID());e.setWalletId(w.getId());
  e.setPaymentId(payment);e.setEntryType(type);e.setAmountMinor(amount);e.setAvailableBalanceAfterMinor(w.getAvailableBalanceMinor());
  e.setReservedBalanceAfterMinor(w.getReservedBalanceMinor());e.setCreatedAt(Instant.now());ledger.save(e);
  outbox.add(Topics.WALLET_LEDGER,w.getUserId().toString(),new WalletLedgerEvent(UUID.randomUUID(),w.getId(),w.getUserId(),payment,type.name(),amount,
   w.getAvailableBalanceMinor(),w.getReservedBalanceMinor(),Instant.now()));metrics.counter("payflow.wallet.operations","phase",type.name()).increment();}
 private WalletResponse replay(UUID user,UUID payment,LedgerEntryType type,long amount){if(payment==null)throw new BusinessException("PAYMENT_ID_REQUIRED","paymentId is required",400);
  var old=ledger.findByPaymentIdAndEntryType(payment,type);if(old.isEmpty())return null;if(old.get().getAmountMinor()!=amount)
   throw new BusinessException("IDEMPOTENCY_CONFLICT","Amount differs from original operation",409);return view(require(user));}
 private void reservation(UUID payment,long amount,LedgerEntryType opposite){
  var reserve=ledger.findByPaymentIdAndEntryType(payment,LedgerEntryType.RESERVE);
  if(reserve.isEmpty())throw new BusinessException("RESERVATION_NOT_FOUND","No reservation exists for payment",409);
  if(reserve.get().getAmountMinor()!=amount)throw new BusinessException("RESERVATION_MISMATCH","Amount differs from reservation",409);
  if(ledger.findByPaymentIdAndEntryType(payment,opposite).isPresent())
   throw new BusinessException("RESERVATION_ALREADY_RESOLVED","Reservation already has a terminal phase",409);
 }
 private Wallet locked(UUID user){return wallets.findByUserIdForUpdate(user).orElseThrow(()->new BusinessException("WALLET_NOT_FOUND","Wallet not found",404));}
 private Wallet require(UUID user){return wallets.findByUserId(user).orElseThrow(()->new BusinessException("WALLET_NOT_FOUND","Wallet not found",404));}
 private WalletResponse view(Wallet w){return new WalletResponse(w.getId(),w.getUserId(),w.getAvailableBalanceMinor(),w.getReservedBalanceMinor(),w.getCurrency(),w.getVersion());}
}
