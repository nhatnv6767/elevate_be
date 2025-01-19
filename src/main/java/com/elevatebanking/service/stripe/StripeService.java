package com.elevatebanking.service.stripe;

import com.elevatebanking.dto.atm.AtmDTOs;
import com.elevatebanking.exception.PaymentProcessingException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret.key}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey; // Set Stripe API key globally
    }

    public PaymentIntent createPaymentIntent(BigDecimal amount, String currency, String paymentMethodId,
                                             Map<String, String> metadata) throws StripeException {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(new BigDecimal("100")).longValue())
                .setCurrency(currency.toLowerCase())
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .putAllMetadata(metadata)
                .build();
        return PaymentIntent.create(params);

    }

    public String createPaymentMethod(AtmDTOs.StripeDepositRequest.CardInfo cardInfo) {
        try {
            Stripe.apiKey = secretKey;

            PaymentMethodCreateParams params = PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.CARD)
                    .setCard(PaymentMethodCreateParams.CardDetails.builder()
                            .setNumber(cardInfo.getNumber())
                            .setExpMonth(Long.valueOf(cardInfo.getExpMonth()))
                            .setExpYear(Long.valueOf(cardInfo.getExpYear()))
                            .setCvc(cardInfo.getCvc())
                            .build())
                    .build();

            PaymentMethod paymentMethod = PaymentMethod.create(params);
            log.debug("Created payment method: {}", paymentMethod.getId());
            return paymentMethod.getId();
        } catch (StripeException e) {
            log.error("Failed to create payment method: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to create payment method: " + e.getMessage());
        }
    }

    public Event validateWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
}
