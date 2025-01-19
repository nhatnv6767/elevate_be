package com.elevatebanking.controller.stripe;

import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.stripe.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stripe/webhook")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {
    private final StripeService stripeService;
    private final TransactionRepository transactionRepository;
    private final IAccountService accountService;

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            Event event = stripeService.validateWebhookEvent(payload, sigHeader);
            Object stripeObject = event.getData().getObject();
            switch (event.getType()) {
                case "payment_intent.created":
                    if (stripeObject instanceof PaymentIntent) {
                        handlePaymentIntentCreated((PaymentIntent) stripeObject);
                    }
                    break;
                case "payment_intent.succeeded":
                    if (stripeObject instanceof PaymentIntent) {
                        handleSuccessfulPayment((PaymentIntent) stripeObject);
                    }
                    break;
                case "charge.succeeded":
                    if (stripeObject instanceof Charge) {
                        handleChargeSucceeded((Charge) stripeObject);
                    }
                    break;
                case "payment_intent.payment_failed":
                    if (stripeObject instanceof PaymentIntent) {
                        handleFailedPayment((PaymentIntent) stripeObject);
                    }
                    break;
                default:
                    log.warn("Unhandled event type: {}", event.getType());
            }
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ClassCastException e) {
            log.error("Error casting stripe object: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private void handleChargeSucceeded(Charge charge) {
        try {
            String paymentIntentId = charge.getPaymentIntent();
            if (paymentIntentId != null) {
                PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
                handleSuccessfulPayment(paymentIntent);
            }
        } catch (StripeException e) {
            log.error("Error retrieving PaymentIntent", e);
        }
    }


    private void handleSuccessfulPayment(PaymentIntent paymentIntent) {
        String transactionId = paymentIntent.getMetadata().get("transactionId");
        if (transactionId != null) {
            Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
            if (transaction.getStatus() != TransactionStatus.COMPLETED) {
                transaction.setStatus(TransactionStatus.COMPLETED);
                transactionRepository.save(transaction);
            }
        }
    }

    private void handleFailedPayment(PaymentIntent paymentIntent) {
        String transactionId = paymentIntent.getMetadata().get("transactionId");
        if (transactionId != null) {
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

            if (transaction.getStatus() != TransactionStatus.FAILED) {
                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);
            }
        }
    }


    private void handlePaymentIntentCreated(PaymentIntent paymentIntent) {
        // Log payment intent created
        log.info("Payment Intent created: {}", paymentIntent.getId());

        // Get metadata from payment intent
        Map<String, String> metadata = paymentIntent.getMetadata();
        String accountNumber = metadata.get("accountNumber");
        String amount = metadata.get("amount");

        // Log payment details for tracking
        log.info("New payment initiated - Account: {}, Amount: {}",
                accountNumber, amount);

    }
}
