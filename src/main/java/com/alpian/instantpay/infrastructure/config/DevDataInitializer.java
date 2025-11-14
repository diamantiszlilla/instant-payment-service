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

import java.util.List;

@Slf4j
@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class DevDataInitializer {
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final DevSeedProperties devSeedProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void seedData() {
        log.info("DEV MODE: DevDataInitializer started");
        DevSeedProperties.Participant senderConfig = devSeedProperties.getSender();
        DevSeedProperties.Participant recipientConfig = devSeedProperties.getRecipient();
        String currency = devSeedProperties.getAccountCurrency();

        if (userRepository.count() == 0) {
            log.warn("DEV MODE: Creating default test users and accounts");

            UserEntity senderUser = UserEntity.builder()
                    .username(senderConfig.getUsername())
                    .passwordHash(passwordEncoder.encode(senderConfig.getPassword()))
                    .role(senderConfig.getRole())
                    .build();

            senderUser = userRepository.save(senderUser);
            log.info("Default sender user created: username={}, password={}", senderConfig.getUsername(), senderConfig.getPassword());

            AccountEntity senderAccount = AccountEntity.builder()
                    .user(senderUser)
                    .accountNumber(senderConfig.getAccountNumber())
                    .balance(senderConfig.getInitialBalance())
                    .currency(currency)
                    .build();

            AccountEntity savedSenderAccount = accountRepository.save(senderAccount);
            log.info("Default sender account created: accountNumber={}, balance={} {}, accountId={}",
                    senderConfig.getAccountNumber(),
                    senderConfig.getInitialBalance(),
                    currency,
                    savedSenderAccount.getId());

            UserEntity recipientUser = UserEntity.builder()
                    .username(recipientConfig.getUsername())
                    .passwordHash(passwordEncoder.encode(recipientConfig.getPassword()))
                    .role(recipientConfig.getRole())
                    .build();

            recipientUser = userRepository.save(recipientUser);
            log.info("Default recipient user created: username={}, password={}", recipientConfig.getUsername(), recipientConfig.getPassword());

            AccountEntity recipientAccount = AccountEntity.builder()
                    .user(recipientUser)
                    .accountNumber(recipientConfig.getAccountNumber())
                    .balance(recipientConfig.getInitialBalance())
                    .currency(currency)
                    .build();

            AccountEntity savedRecipientAccount = accountRepository.save(recipientAccount);
            log.info("Default recipient account created: accountNumber={}, balance={} {}, accountId={}",
                    recipientConfig.getAccountNumber(),
                    recipientConfig.getInitialBalance(),
                    currency,
                    savedRecipientAccount.getId());
        } else {
            userRepository.findByUsername(senderConfig.getUsername()).ifPresent(user -> {
                if (accountRepository.findByUserId(user.getId()).isEmpty()) {
                    log.warn("DEV MODE: User exists but has no account. Creating default account.");

                    AccountEntity defaultAccount = AccountEntity.builder()
                            .user(user)
                            .accountNumber(senderConfig.getAccountNumber())
                            .balance(senderConfig.getInitialBalance())
                            .currency(currency)
                            .build();

                    AccountEntity savedAccount = accountRepository.save(defaultAccount);
                    log.info("Default test account created for existing user: accountNumber={}, balance={} {}, accountId={}",
                            senderConfig.getAccountNumber(),
                            senderConfig.getInitialBalance(),
                            currency,
                            savedAccount.getId());
                }
            });

            UserEntity recipientUser = userRepository.findByUsername(recipientConfig.getUsername())
                    .orElseGet(() -> {
                        log.warn("DEV MODE: Recipient user does not exist. Creating recipient user.");
                        UserEntity newRecipient = UserEntity.builder()
                                .username(recipientConfig.getUsername())
                                .passwordHash(passwordEncoder.encode(recipientConfig.getPassword()))
                                .role(recipientConfig.getRole())
                                .build();
                        return userRepository.save(newRecipient);
                    });

            if (accountRepository.findByUserId(recipientUser.getId()).isEmpty()) {
                log.warn("DEV MODE: Recipient user exists but has no account. Creating recipient account.");

                AccountEntity recipientAccount = AccountEntity.builder()
                        .user(recipientUser)
                        .accountNumber(recipientConfig.getAccountNumber())
                        .balance(recipientConfig.getInitialBalance())
                        .currency(currency)
                        .build();

                AccountEntity savedRecipientAccount = accountRepository.save(recipientAccount);
                log.info("Recipient account created: accountNumber={}, balance={} {}, accountId={}",
                        recipientConfig.getAccountNumber(),
                        recipientConfig.getInitialBalance(),
                        currency,
                        savedRecipientAccount.getId());
            }
        }
        
        log.info("DEV MODE: Checking existing test accounts...");
        userRepository.findByUsername(senderConfig.getUsername()).ifPresent(user -> {
            List<AccountEntity> accounts = accountRepository.findByUserId(user.getId());
            accounts.forEach(account -> 
                log.info("DEV MODE: Sender account available - accountId={}, accountNumber={}, balance={} {}", 
                    account.getId(), account.getAccountNumber(), account.getBalance(), account.getCurrency())
            );
        });
        
        userRepository.findByUsername(recipientConfig.getUsername()).ifPresent(user -> {
            List<AccountEntity> accounts = accountRepository.findByUserId(user.getId());
            accounts.forEach(account -> 
                log.info("DEV MODE: Recipient account available - accountId={}, accountNumber={}, balance={} {}", 
                    account.getId(), account.getAccountNumber(), account.getBalance(), account.getCurrency())
            );
        });
        
        log.info("DEV MODE: DevDataInitializer completed. Use GET /api/accounts to list your accounts.");
    }
}
