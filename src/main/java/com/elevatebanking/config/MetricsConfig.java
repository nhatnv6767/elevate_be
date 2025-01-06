package com.elevatebanking.config;

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
                .register(registry);
    }

    @Bean
    public Timer lockAcquisitionTimer(MeterRegistry registry) {
        return Timer.builder("transaction.lock.acquisition.time")
                .description("Time spent acquiring locks")
                .register(registry);
    }
}
