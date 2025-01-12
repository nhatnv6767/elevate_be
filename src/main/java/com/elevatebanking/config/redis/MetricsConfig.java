package com.elevatebanking.config.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    @Bean
    public Counter lockAcquisitionFailures(MeterRegistry registry) {
        return Counter.builder("transaction.lock.acquisition.failures")
                .description("Number of failed lock acquisitions")
                .tag("type", "redis")
                .register(registry);
    }

    @Bean
    public Timer lockAcquisitionTimer(MeterRegistry registry) {
        return Timer.builder("transaction.lock.acquisition.time")
                .description("Time spent acquiring locks")
                .tag("type", "redis")
                .publishPercentiles(0.5, 0.95, 0.99) // Add percentiles tracking
                .register(registry);
    }

    // Redis connection metrics
    @Bean
    public Counter redisConnectionFailures(MeterRegistry registry) {
        return Counter.builder("redis.connection.failures")
                .description("Number of Redis connection failures")
                .register(registry);
    }

    @Bean
    public Timer redisOperationTimer(MeterRegistry registry) {
        return Timer.builder("redis.operation.time")
                .description("Time spent on Redis operations")
                .tag("operation", "default")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // Transaction metrics
    @Bean
    public Timer transactionTimer(MeterRegistry registry) {
        return Timer.builder("transaction.processing.time")
                .description("Time spent processing transactions")
                .tag("type", "withdrawal")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public Counter transactionFailures(MeterRegistry registry) {
        return Counter.builder("transaction.failures")
                .description("Number of failed transactions")
                .tag("type", "withdrawal")
                .register(registry);
    }

    @Bean
    public Timer retryTimer(MeterRegistry registry) {
        return Timer.builder("transaction.retry.time")
                .description("Time spent in retry operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
