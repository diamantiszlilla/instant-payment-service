package com.alpian.instantpay.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "dev.seed")
public class DevSeedProperties {

    private Participant sender = new Participant();
    private Participant recipient = new Participant();
    private String accountCurrency;

    @Getter
    @Setter
    public static class Participant {
        private String username;
        private String password;
        private String role;
        private String accountNumber;
        private BigDecimal initialBalance;
    }
}
