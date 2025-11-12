package com.alpian.instantpay.infrastructure.config;

import com.alpian.instantpay.infrastructure.persistence.entity.AccountEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.UserEntity;
import com.alpian.instantpay.infrastructure.persistence.repository.AccountRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class DevDataInitializer {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public void seedData() {
        if (userRepository.count() == 0) {
            log.warn("DEV MODE: Creating default test users and accounts");

            UserEntity senderUser = UserEntity.builder()
                    .username("user")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .role("USER")
                    .build();

            senderUser = userRepository.save(senderUser);
            log.info("Default test user created: username=user, password=password123");

            AccountEntity senderAccount = AccountEntity.builder()
                    .user(senderUser)
                    .accountNumber("DEV-ACCOUNT-001")
                    .balance(new BigDecimal("10000.00"))
                    .currency("USD")
                    .build();

            AccountEntity savedSenderAccount = accountRepository.save(senderAccount);
            log.info("Default sender account created: accountNumber=DEV-ACCOUNT-001, balance=10000.00 USD, accountId={}", savedSenderAccount.getId());

            UserEntity recipientUser = UserEntity.builder()
                    .username("recipient")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .role("USER")
                    .build();

            recipientUser = userRepository.save(recipientUser);
            log.info("Default test user created: username=recipient, password=password123");

            AccountEntity recipientAccount = AccountEntity.builder()
                    .user(recipientUser)
                    .accountNumber("DEV-ACCOUNT-002")
                    .balance(new BigDecimal("5000.00"))
                    .currency("USD")
                    .build();

            AccountEntity savedRecipientAccount = accountRepository.save(recipientAccount);
            log.info("Default recipient account created: accountNumber=DEV-ACCOUNT-002, balance=5000.00 USD, accountId={}", savedRecipientAccount.getId());
        } else {
            userRepository.findByUsername("user").ifPresent(user -> {
                if (accountRepository.findByUserId(user.getId()).isEmpty()) {
                    log.warn("DEV MODE: User exists but has no account. Creating default account.");

                    AccountEntity defaultAccount = AccountEntity.builder()
                            .user(user)
                            .accountNumber("DEV-ACCOUNT-001")
                            .balance(new BigDecimal("10000.00"))
                            .currency("USD")
                            .build();

                    AccountEntity savedAccount = accountRepository.save(defaultAccount);
                    log.info("Default test account created for existing user: accountNumber=DEV-ACCOUNT-001, balance=10000.00 USD, accountId={}", savedAccount.getId());
                }
            });

            UserEntity recipientUser = userRepository.findByUsername("recipient")
                    .orElseGet(() -> {
                        log.warn("DEV MODE: Recipient user does not exist. Creating recipient user.");
                        UserEntity newRecipient = UserEntity.builder()
                                .username("recipient")
                                .passwordHash(passwordEncoder.encode("password123"))
                                .role("USER")
                                .build();
                        return userRepository.save(newRecipient);
                    });

            if (accountRepository.findByUserId(recipientUser.getId()).isEmpty()) {
                log.warn("DEV MODE: Recipient user exists but has no account. Creating recipient account.");

                AccountEntity recipientAccount = AccountEntity.builder()
                        .user(recipientUser)
                        .accountNumber("DEV-ACCOUNT-002")
                        .balance(new BigDecimal("5000.00"))
                        .currency("USD")
                        .build();

                AccountEntity savedRecipientAccount = accountRepository.save(recipientAccount);
                log.info("Recipient account created: accountNumber=DEV-ACCOUNT-002, balance=5000.00 USD, accountId={}", savedRecipientAccount.getId());
            }
        }
    }
}
