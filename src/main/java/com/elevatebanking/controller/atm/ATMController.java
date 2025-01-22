package com.elevatebanking.controller.atm;

import com.elevatebanking.dto.atm.AtmDTOs.*;
import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.atm.AtmMachine;
import com.elevatebanking.entity.log.AuditLog;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.event.EmailType;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.PaymentProcessingException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.exception.TooManyAttemptsException;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import com.elevatebanking.service.atm.AtmCacheService;
import com.elevatebanking.service.atm.AtmManagementService;
import com.elevatebanking.service.email.EmailEventService;
import com.elevatebanking.service.nonImp.AuditLogService;
import com.elevatebanking.service.nonImp.EmailService;
import com.elevatebanking.service.stripe.StripeAsyncService;
import com.elevatebanking.service.stripe.StripeService;
import com.elevatebanking.service.transaction.TransactionValidationService;
import com.elevatebanking.service.transaction.config.TransactionLockManager;
import com.elevatebanking.util.SecurityUtils;
import com.github.dockerjava.api.exception.UnauthorizedException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/v1/atm")
@Tag(name = "ATM Operations", description = "APIs for ATM operations like withdraw and deposit")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
@Slf4j
public class ATMController {
    private final ITransactionService transactionService;
    private final SecurityUtils securityUtils;
    private final IAccountService accountService;
    private final TransactionValidationService validationService;
    private final AuditLogService auditLogService;
    private final AtmManagementService atmManagementService;
    private final EmailService emailService;
    private final EmailEventService emailEventService;
    private final StripeService stripeService;
    private final AtmCacheService atmCacheService;
    private final StripeAsyncService stripeAsyncService;

    private static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("1");
    private static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("5");

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Withdraw money from ATM")
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@Valid @RequestBody WithdrawRequest request) throws InterruptedException {
        String userId = securityUtils.getCurrentUserId();
        String lockKey = "atm_operation:" + userId;

        try (TransactionLockManager lockManager = new TransactionLockManager(lockKey, validationService)) {
            if (!lockManager.acquireLock()) {
                throw new TooManyAttemptsException("ATM is busy, please try again in a few seconds");
            }

            // validate account ownership
            Account account = accountService.getAccountByNumber(request.getAccountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

            if (!accountService.isAccountOwner(account.getId(), userId)) {
                throw new UnauthorizedException("Not authorized to access this account");
            }

            if (request.getAtmId() == null || request.getAtmId().trim().isEmpty()) {
                throw new InvalidOperationException("ATM ID cannot be null or empty");
            }

            // validate ATM exists and is active
//            AtmMachine atm = atmManagementService.getAtmById(request.getAtmId());
            AtmMachine atm = atmCacheService.getAtmWithCache(request.getAtmId());
            if (!"ACTIVE".equals(atm.getStatus())) {
                throw new InvalidOperationException("ATM is not active");
            }

            // validate withdrawal amount and denominations
            // BigDecimal totalAmount = calculateTotal(request.getRequestedDenominations());
            // if (!totalAmount.equals(request.getAmount())) {
            // throw new InvalidOperationException("Requested denominations don't match the
            // total amount");
            // }

            // validate withdrawal amount
            BigDecimal amount = request.getAmount();
            BigDecimal remainder = amount.remainder(MIN_WITHDRAWAL_AMOUNT);
            boolean isNotMultipleOfMinAmount = remainder.compareTo(BigDecimal.ZERO) != 0;

            if (amount.compareTo(MIN_WITHDRAWAL_AMOUNT) < 0 || isNotMultipleOfMinAmount) {
                throw new InvalidOperationException("Withdrawal amount must be at least $" + MIN_WITHDRAWAL_AMOUNT
                        + " and in multiples of $" + MIN_WITHDRAWAL_AMOUNT);
            }

            if (amount.compareTo(MAX_WITHDRAWAL_AMOUNT) > 0) {
                throw new InvalidOperationException("Withdrawal amount must be at most $" + MAX_WITHDRAWAL_AMOUNT);
            }

            // calculate amount money user wants to withdraw
            Map<Integer, Integer> requestedDenominations = atmManagementService
                    .calculateOptimalDenominations(request.getAtmId(), request.getAmount());

            // check if atm has enough bills of requested denominations
            if (!atmManagementService.canProcessWithdrawal(request.getAtmId(), request.getAmount(),
                    requestedDenominations)) {
                throw new InvalidOperationException("ATM doesn't have enough bills to process this withdrawal");
            }

            request.setRequestedDenominations(requestedDenominations);

            // process the withdrawal
            WithdrawalResponse response = transactionService.withdraw(request);

            // update ATM denominations
            Map<Integer, Integer> updatedDenominations = atmManagementService
                    .subtractDenominations(atm.getDenominations(), requestedDenominations);
            atmManagementService.updateAtmDenominations(response.getAtmId(), updatedDenominations);

            // log successful withdrawal
            auditLogService.logEvent(
                    userId,
                    "ATM_WITHDRAWAL",
                    "TRANSACTION",
                    response.getTransactionId(),
                    request,
                    Map.of(
                            "status", "SUCCESS",
                            "amount", request.getAmount(),
                            "atmId", request.getAtmId(),
                            "dispensedDenominations", requestedDenominations),
                    AuditLog.AuditStatus.SUCCESS);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing withdrawal for user: {}", userId, e);
            auditLogService.logEvent(
                    userId,
                    "ATM_WITHDRAWAL_FAILED",
                    "TRANSACTION",
                    null,
                    request,
                    Map.of(
                            "error", e.getMessage(),
                            "location", "ATM_001"),
                    AuditLog.AuditStatus.FAILED);
            throw e;
        }
    }

    @PostMapping("/deposit/stripe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> stripeDeposit(@Valid @RequestBody StripeDepositRequest request)
            throws StripeException, InterruptedException, ExecutionException, TimeoutException {
        String userId = securityUtils.getCurrentUserId();
        String lockKey = "stripe_deposit:" + userId;

        try (TransactionLockManager lockManager = new TransactionLockManager(lockKey, validationService)) {
            if (!lockManager.acquireLock()) {
                throw new TooManyAttemptsException("System is busy, please try again in a few seconds");
            }

            // validate account
            Account account = accountService.getAccountByNumber(request.getAccountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format("Account %s not found", request.getAccountNumber())));
            if (!accountService.isAccountOwner(account.getId(), userId)) {
                throw new UnauthorizedException("Not authorized to access this account");
            }

            String paymentMethodId = request.getPaymentMethodId();
            if (paymentMethodId == null && request.getCardInfo() != null) {
                paymentMethodId = stripeService.createPaymentMethod(request.getCardInfo());
                request.setPaymentMethodId(paymentMethodId);
            }

            if (paymentMethodId == null) {
                throw new InvalidOperationException("Payment method information is required");
            }

            // create payment metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", userId);
            metadata.put("accountNumber", request.getAccountNumber());
            metadata.put("description", request.getDescription());
            Map<String, String> stripeMetadata = new HashMap<>();
            metadata.forEach((key, value) -> stripeMetadata.put(key, String.valueOf(value)));

            // process stripe
//            PaymentIntent paymentIntent = stripeService.createPaymentIntent(
//                    request.getAmount(),
//                    request.getCurrency(),
//                    paymentMethodId,
//                    stripeMetadata);

            CompletableFuture<PaymentIntent> paymentFuture = stripeAsyncService.processPayment(request, stripeMetadata);

            TransactionResponse transaction = transactionService.deposit(request);
            PaymentIntent paymentIntent = paymentFuture.get(5, TimeUnit.SECONDS);

            if ("succeeded".equals(paymentIntent.getStatus())) {


                sendDepositConfirmation(account, transaction, paymentIntent);

                auditLogService.logEvent(
                        userId,
                        "STRIPE_DEPOSIT",
                        "TRANSACTION",
                        transaction.getTransactionId(),
                        request,
                        Map.of(
                                "status", "SUCCESS",
                                "amount", request.getAmount(),
                                "paymentIntentId", paymentIntent.getId()),
                        AuditLog.AuditStatus.SUCCESS);
                return ResponseEntity.ok(StripeDepositResponse.stripeBuilder()
                        .transactionId(transaction.getTransactionId())
                        .paymentIntentId(paymentIntent.getId())
                        .type("STRIPE_DEPOSIT")
                        .amount(request.getAmount())
                        .status("SUCCESS")
                        .fromAccount(paymentMethodId)
                        .toAccount(request.getAccountNumber())
                        .description(request.getDescription())
                        .timestamp(LocalDateTime.now())
                        .paymentStatus(paymentIntent.getStatus())
                        .paymentMetadata(metadata)
                        .build());

            }

            throw new PaymentProcessingException("Payment processing failed");
        } catch (Exception e) {
            log.error("Stripe deposit failed for user: {}", userId, e);
            auditLogService.logEvent(
                    userId,
                    "STRIPE_DEPOSIT_FAILED",
                    "TRANSACTION",
                    null,
                    request,
                    Map.of("error", e.getMessage()),
                    AuditLog.AuditStatus.FAILED);
            throw e;
        }
    }

    private void sendDepositConfirmation(Account account, TransactionResponse transaction,
                                         PaymentIntent paymentIntent) {
        try {
            String subject = "Deposit Confirmation";
            String content = String.format(
                    "Dear %s,\n\n" +
                            "Your deposit of %s %s has been processed successfully.\n\n" +
                            "Transaction Details:\n" +
                            "- Transaction ID: %s\n" +
                            "- Payment ID: %s\n" +
                            "- Account: %s\n" +
                            "- Amount: %s %s\n" +
                            "- Date: %s\n\n" +
                            "Current Balance: %s\n\n" +
                            "Thank you for using our service.\n\n" +
                            "Best regards,\n" +
                            "Elevate Banking Team",
                    account.getUser().getFullName(),
                    transaction.getAmount(),
                    paymentIntent.getCurrency().toUpperCase(),
                    transaction.getTransactionId(),
                    paymentIntent.getId(),
                    account.getAccountNumber(),
                    transaction.getAmount(),
                    paymentIntent.getCurrency().toUpperCase(),
                    transaction.getTimestamp() != null ? transaction.getTimestamp() : LocalDateTime.now(),
                    account.getBalance());

//        EmailEvent emailEvent = EmailEvent.builder()
//                .to(account.getUser().getEmail())
//                .subject(subject)
//                .content(content)
//                .type(EmailType.TRANSACTION)
//                .deduplicationId(transaction.getTransactionId())
//                .build();

            EmailEvent emailEvent = EmailEvent.createTransactionEmail(account.getUser().getEmail(), subject, content);
            emailEvent.setDeduplicationId(transaction.getTransactionId());
            emailEventService.sendEmailEvent(emailEvent);
        } catch (Exception e) {
            log.error("Failed to send deposit confirmation email", e);
        }
    }

}
