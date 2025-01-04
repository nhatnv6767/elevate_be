package com.elevatebanking.service.transaction.config;

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "transaction.limits")
@Data
public class TransactionLimitConfig {
    private Map<String, TierLimit> tiers = new HashMap<>();

    @Data
    @Builder
    public static class TierLimit {
        private BigDecimal singleTransactionLimit;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private int maxTransactionsPerMinute;
        private int maxTransactionsPerDay;
    }
}
