package com.payflow.wallet.kafka;

import com.payflow.events.Topics;
import com.payflow.events.UserRegisteredEvent;
import com.payflow.wallet.service.WalletMoneyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredListener {
    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);
    private final WalletMoneyService wallets;

    public UserRegisteredListener(WalletMoneyService wallets) {
        this.wallets = wallets;
    }

    @KafkaListener(topics = Topics.USER_REGISTERED, groupId = "wallet-service")
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Opening wallet for user {}", event.userId());
        wallets.openWallet(event.userId(), "INR");
    }
}
