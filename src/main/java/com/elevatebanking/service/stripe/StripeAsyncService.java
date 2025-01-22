package com.elevatebanking.service.stripe;

import com.elevatebanking.dto.atm.AtmDTOs;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class StripeAsyncService {
    private final StripeService stripeService;

    @Async("paymentExecutor")
    public CompletableFuture<PaymentIntent> processPayment(AtmDTOs.StripeDepositRequest request, Map<String, String> metadata) {
        return CompletableFuture.supplyAsync(() ->
        {
            try {
                return stripeService.createPaymentIntent(request.getAmount(), request.getCurrency(),
                        request.getPaymentMethodId(), metadata);
            } catch (StripeException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
