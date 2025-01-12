package com.elevatebanking.config.redis;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@Slf4j
public class RedisRetryConfig {
    @Value("${redis.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${redis.retry.initial-interval:100}")
    private long initialInterval;

    @Value("${redis.retry.max-interval:1000}")
    private long maxInterval;

    @Value("${redis.retry.multiplier:2}")
    private double multiplier;

    @Bean
    public RetryTemplate redisRetryTemplate(MeterRegistry meterRegistry) {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxInterval);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);

        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Add retry listener for metrics
        retryTemplate.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                meterRegistry.counter("redis.retry.attempts")
                        .increment();
            }
        });

        return retryTemplate;
    }
}
