package com.elevatebanking.config.stripe;

import com.elevatebanking.service.stripe.StripeService;
import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {
    @Value("${stripe.publishable.key}")
    private String publishableKey;

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret.key}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    @Bean
    public StripeService stripeService() {
        return new StripeService();
    }

}

// dont need this function
