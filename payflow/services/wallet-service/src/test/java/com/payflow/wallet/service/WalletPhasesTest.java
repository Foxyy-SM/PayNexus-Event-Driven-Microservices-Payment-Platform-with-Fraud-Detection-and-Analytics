package com.payflow.wallet.service;
import com.payflow.wallet.domain.*;
import com.payflow.wallet.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class WalletPhasesTest {
 @Mock WalletRepository wallets;@Mock LedgerEntryRepository ledger;@Mock OutboxService outbox;WalletMoneyService service;
 @BeforeEach void setup(){service=new WalletMoneyService(wallets,ledger,outbox,new SimpleMeterRegistry());}
 @Test void reserveAndCaptureUseExactMinorUnits(){UUID user=UUID.randomUUID(),payment=UUID.randomUUID();Wallet w=wallet(user,4000,0);
  when(ledger.findByPaymentIdAndEntryType(payment,LedgerEntryType.RESERVE)).thenReturn(Optional.empty()).thenReturn(Optional.of(entry(payment,1000)));
  when(ledger.findByPaymentIdAndEntryType(payment,LedgerEntryType.CAPTURE)).thenReturn(Optional.empty());
  when(wallets.findByUserIdForUpdate(user)).thenReturn(Optional.of(w));when(wallets.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
  assertEquals(3000,service.reserve(user,1000,payment).availableBalanceMinor());
  var captured=service.capture(user,1000,payment);assertEquals(0,captured.reservedBalanceMinor());assertEquals(3000,captured.availableBalanceMinor());}
 @Test void reserveRejectsInsufficientFunds(){UUID user=UUID.randomUUID(),payment=UUID.randomUUID();when(ledger.findByPaymentIdAndEntryType(payment,LedgerEntryType.RESERVE)).thenReturn(Optional.empty());
  when(wallets.findByUserIdForUpdate(user)).thenReturn(Optional.of(wallet(user,5,0)));assertThrows(RuntimeException.class,()->service.reserve(user,10,payment));}
 @Test void returnsAllLedgerPhasesForPaymentInOrder(){UUID payment=UUID.randomUUID();LedgerEntry reserve=entry(payment,1000);
  LedgerEntry capture=entry(payment,1000);capture.setEntryType(LedgerEntryType.CAPTURE);
  when(ledger.findByPaymentIdOrderByCreatedAtAsc(payment)).thenReturn(List.of(reserve,capture));
  var phases=service.phases(payment);assertEquals(List.of(LedgerEntryType.RESERVE,LedgerEntryType.CAPTURE),
   phases.stream().map(p->p.entryType()).toList());}
 private Wallet wallet(UUID user,long available,long reserved){Wallet w=new Wallet();w.setId(UUID.randomUUID());w.setUserId(user);w.setAvailableBalanceMinor(available);
  w.setReservedBalanceMinor(reserved);w.setCurrency("INR");w.setCreatedAt(Instant.now());return w;}
 private LedgerEntry entry(UUID payment,long amount){LedgerEntry e=new LedgerEntry();e.setId(UUID.randomUUID());e.setPaymentId(payment);e.setEntryType(LedgerEntryType.RESERVE);e.setAmountMinor(amount);return e;}
}
