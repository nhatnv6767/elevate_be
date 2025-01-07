package com.elevatebanking.service.processor;

import com.elevatebanking.config.kafka.KafkaEventSender;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.NotificationEvent;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.NonRetryableException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import com.elevatebanking.service.notification.NotificationDeliveryService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProcessor {
    private final ITransactionService transactionService;
    private final IAccountService accountService;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final KafkaTemplate<String, NotificationEvent> notificationEventKafkaTemplate;
    private final KafkaEventSender kafkaEventSender;
    private final NotificationDeliveryService notificationDeliveryService;
    private final Cache<String, TransactionEvent> processedEvents = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    //
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String MAIN_TOPIC = "elevate.transactions";
    private static final String RETRY_TOPIC = "elevate.transactions.retry";
    private static final String DLQ_TOPIC = "elevate.transactions.dlq";

    @KafkaListener(
            topics = MAIN_TOPIC,
            groupId = "elevate-transaction-group",
            containerFactory = "transactionKafkaListenerContainerFactory",
            properties = {
                    "max.poll.records=1",
                    "enable.auto.commit=false",
            }
    )
    public void processTransactionEvent(TransactionEvent event, Acknowledgment ack) {
        MDC.put("transactionId", event.getTransactionId());
        log.info("Processing transaction event: {}", event);
        try {
            event.addProcessStep("EVENT_RECEIVED");

            if (event.isExpired()) {
                event.setError("Event is expired");
                sendToDLQ(event, "Event is expired");
                log.warn("Expired event detected: {}", event);
                ack.acknowledge();
                return;
            }

            if (isDuplicateEvent(event)) {
                event.addProcessStep("DUPLICATE_DETECTED");
                log.warn("Duplicate event detected: {}", event);
                ack.acknowledge();
                return;
            }

            Transaction transaction = transactionRepository
                    .findByIdForUpdate(event.getTransactionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + event.getTransactionId()));

            if (!isValidStateTransition(transaction.getStatus(), event)) {
                if (event.isRetryable()) {
                    event.incrementRetryCount();
                    sendToRetryTopic(event);
                } else {
                    sendToDLQ(event, "Invalid state transition");
                }
                log.warn("Invalid state transition: {} -> {}", transaction.getStatus(), event.getStatus());
                ack.acknowledge();
                return;
            }

            switch (event.getEventType()) {
                case "transaction.initiated":
                    handleTransactionInitiated(event);
                    break;
                case "transaction.completed":
                    handleTransactionCompleted(event);
                    break;
                case "transaction.failed":
                    handleTransactionFailed(event);
                    break;
                default:
                    log.warn("Unknown event type: {}", event.getEventType());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing transaction event: {}", event, e);
            handleProcessingError(event, e, ack);
        }
    }

    private boolean isDuplicateEvent(TransactionEvent event) {
//        String eventKey = event.getTransactionId() + "-" + event.getEventType();
        String eventKey = String.format("%s-%s-%d-%s",
                event.getTransactionId(),
                event.getEventType(),
                event.getRetryCount(),
                event.getTimestamp().toString()
        );
        event.addMetadata("deduplication_key", eventKey);
        event.addProcessStep("DEDUPLICATION_CHECK");
        return processedEvents.getIfPresent(eventKey) != null;
    }

    private boolean isValidStateTransition(TransactionStatus currentStatus, TransactionEvent event) {

        event.addProcessStep(String.format("STATE_TRANSITION_CHECK: %s -> %s", currentStatus, event.getStatus()));

        Map<TransactionStatus, Set<TransactionStatus>> validTransitions = Map.of(
                TransactionStatus.PENDING, Set.of(TransactionStatus.COMPLETED, TransactionStatus.FAILED),
                TransactionStatus.FAILED, Set.of(TransactionStatus.ROLLED_BACK),
                TransactionStatus.COMPLETED, Set.of()
        );

        boolean isValid = validTransitions.getOrDefault(currentStatus, Set.of()).contains(event.getStatus());
        if (!isValid) {
            event.setError(String.format("Invalid state transition: %s -> %s", currentStatus, event.getStatus()));
        }

        return isValid;
    }

    @KafkaListener(
            topics = RETRY_TOPIC,
            groupId = "${spring.kafka.consumer.groups.transaction-retry}",
            containerFactory = "transactionKafkaListenerContainerFactory"
    )
    public void processRetryEvent(TransactionEvent event, Acknowledgment ack) {
        log.info("Processing retry event: {}", event);
        try {
            // check retry count
            if (event.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                log.warn("Max retry attempts exceeded: {}", event);
                sendToDLQ(event, "Max retry attempts exceeded");
                ack.acknowledge();
                return;
            }

            if (event.getNextRetryAt() != null && LocalDateTime.now().isBefore(event.getNextRetryAt())) {
                log.info("Too early for retry, waiting until: {}", event.getNextRetryAt());
                ack.acknowledge();
                return;
            }

            // increment retry count
            event.incrementRetryCount();

            // process transaction
            Transaction transaction = transactionRepository.findById(event.getTransactionId()).orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + event.getTransactionId()));
            if (transaction.getStatus() == TransactionStatus.COMPLETED || transaction.getStatus() == TransactionStatus.FAILED) {
                log.warn("Transaction already completed or failed: {} - {}", transaction.getId(), transaction.getStatus());
                ack.acknowledge();
                return;
            }
            // try to process transaction
            processTransactionEvent(event, ack);

        } catch (Exception e) {
            log.error("Error processing retry event: {}", event, e);
            // if can be retried
            if (event.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                long backoffInterval = calculateBackoffInterval(event.getRetryCount());
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffInterval));
                kafkaTemplate.send(RETRY_TOPIC, event);
            } else {
                sendToDLQ(event, "Max retry attempts exceeded: " + e.getMessage());
            }
            ack.acknowledge();
        }
    }

    private void handleTransactionInitiated(TransactionEvent event) {
        log.info("Handling transaction initiated event: {}", event.getTransactionId());

        try {
            Transaction transaction = transactionRepository.findById(event.getTransactionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + event.getTransactionId()));

            // validate transaction status
            if (transaction.getStatus() != TransactionStatus.PENDING) {
                log.warn("Transaction is not pending: {}", transaction.getId());
                return;
            }

            // process based on transaction type
            switch (transaction.getType()) {
                case TRANSFER:
                    processTransferTransaction(transaction, event);
                    break;
                case DEPOSIT:
                    processDepositTransaction(transaction, event);
                    break;
                case WITHDRAWAL:
                    processWithdrawalTransaction(transaction, event);
                    break;
                default:
                    log.error("Unknown transaction type: {}", transaction.getType());
                    throw new InvalidOperationException("Unknown transaction type: " + transaction.getType());
            }
        } catch (Exception e) {
            log.error("Error handling transaction initiated event: {}", e.getMessage());
            event.setStatus(TransactionStatus.FAILED);
            kafkaTemplate.send("elevate.transactions", "transaction.failed", event);
        }

    }

    private void handleTransactionCompleted(TransactionEvent event) {
        log.info("Handling transaction completed event: {}", event.getTransactionId());
        updateTransactionStatus(event.getTransactionId(), TransactionStatus.COMPLETED);
        sendNotificationEvent(event, buildCompletedMessage(event));

        Map<String, Object> data = Map.of(
                "transactionId", event.getTransactionId(),
                "amount", event.getAmount(),
                "fromAccount", event.getFromAccount(),
                "toAccount", event.getToAccount(),
                "timestamp", event.getTimestamp()

        );
        notificationDeliveryService.sendNotification(event.getUserId(), "TRANSACTION_COMPLETED", data);
    }

    private void handleTransactionFailed(TransactionEvent event) {
        log.info("Handling transaction failed event: {}", event.getTransactionId());
        updateTransactionStatus(event.getTransactionId(), TransactionStatus.FAILED);
        sendFailureNotification(event);
    }

    private void handleProcessingError(TransactionEvent event, Exception e, Acknowledgment ack) {

        event.addProcessStep("ERROR_HANDLING: " + e.getMessage());
        if (e instanceof NonRetryableException || !event.isRetryable()) {
            event.addProcessStep("NON_RETRYABLE_ERROR");
            sendToDLQ(event, "Non-retryable error: " + e.getMessage());
            updateTransactionStatus(event.getTransactionId(), TransactionStatus.FAILED);
            sendFailureNotification(event);
        } else {
            event.addProcessStep("RETRYABLE_ERROR");
            event.incrementRetryCount();

            // count the next retry time
            long backoffInterval = calculateBackoffInterval(event.getRetryCount());
            event.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffInterval));
            sendToDLQ(event, "Retryable error: " + e.getMessage());
        }
        ack.acknowledge();

    }

    private void processTransferTransaction(Transaction transaction, TransactionEvent event) {

        // validate accounts
        Account fromAccount = accountService.getAccountById(transaction.getFromAccount().getId()).get();
        Account toAccount = accountService.getAccountById(transaction.getToAccount().getId()).get();

        // update balances
        BigDecimal newFromBalance = fromAccount.getBalance().subtract(transaction.getAmount());
        BigDecimal newToBalance = toAccount.getBalance().add(transaction.getAmount());

        // update accounts
        accountService.updateBalance(fromAccount.getId(), newFromBalance);
        accountService.updateBalance(toAccount.getId(), newToBalance);

        // update event with new status
        event.setStatus(TransactionStatus.COMPLETED);
        event.updateBalances(newFromBalance, newToBalance);

        // send completion event
        kafkaTemplate.send(MAIN_TOPIC, "transaction.completed", event);
    }

    private void processDepositTransaction(Transaction transaction, TransactionEvent event) {
        Account account = accountService.getAccountById(transaction.getToAccount().getId()).get();
        BigDecimal newBalance = account.getBalance().add(transaction.getAmount());

        accountService.updateBalance(account.getId(), newBalance);

        event.setStatus(TransactionStatus.COMPLETED);
        event.updateBalances(null, newBalance);

        kafkaTemplate.send(MAIN_TOPIC, "transaction.completed", event);
    }

    private void processWithdrawalTransaction(Transaction transaction, TransactionEvent event) {
        Account account = accountService.getAccountById(transaction.getFromAccount().getId()).get();
        BigDecimal newBalance = account.getBalance().subtract(transaction.getAmount());

        accountService.updateBalance(account.getId(), newBalance);

        event.setStatus(TransactionStatus.COMPLETED);
        event.updateBalances(newBalance, null);

        kafkaTemplate.send(MAIN_TOPIC, "transaction.completed", event);
    }

    private boolean needsRollback(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING &&
                (
                        transaction.getType() == TransactionType.TRANSFER ||
                                transaction.getType() == TransactionType.WITHDRAWAL
                );
    }

    private void performRollback(Transaction transaction) {
        try {
            switch (transaction.getType()) {
                case TRANSFER:
                    if (transaction.getFromAccount() != null && transaction.getToAccount() != null) {
                        Account fromAccount = accountService.getAccountById(transaction.getFromAccount().getId()).get();
                        Account toAccount = accountService.getAccountById(transaction.getToAccount().getId()).get();

                        //reverse any partial transfer
                        if (fromAccount.getBalance().compareTo(transaction.getAmount()) < 0) {
                            accountService.updateBalance(toAccount.getId(), toAccount.getBalance().subtract(transaction.getAmount()));
                        }
                    }
                    break;
                case WITHDRAWAL:
                    if (transaction.getFromAccount() != null) {
                        Account account = accountService.getAccountById(transaction.getFromAccount().getId()).get();
                        accountService.updateBalance(account.getId(), account.getBalance().add(transaction.getAmount()));
                    }
                    break;
                default:
                    log.info("No rollback needed for transaction type: {}", transaction.getType());
            }
        } catch (Exception e) {
            log.error("Error rolling back transaction: {}", transaction.getId(), e);
            TransactionEvent event = new TransactionEvent(transaction, "transaction.rollback");
            event.setError("CRITICAL: Manual rollback investigation required");
            sendNotificationEvent(event, "CRITICAL: Manual rollback investigation required");
        }
    }

    private void sendNotificationEvent(TransactionEvent event, String message) {
        log.info("Sending notification event: {} {}", event.getTransactionId(), message);
        try {
            // xac dinh loai thong bao va priority dua tren transaction status
            NotificationEvent.NotificationType notificationType;
            NotificationEvent.Priority priority;

            switch (event.getStatus()) {
                case COMPLETED:
                    notificationType = NotificationEvent.NotificationType.TRANSACTION_COMPLETED;
                    priority = NotificationEvent.Priority.MEDIUM;
                    break;
                case FAILED:
                    notificationType = NotificationEvent.NotificationType.TRANSACTION_FAILED;
                    priority = NotificationEvent.Priority.HIGH;
                    break;
                default:
                    notificationType = NotificationEvent.NotificationType.TRANSACTION_INITIATED;
                    priority = NotificationEvent.Priority.LOW;
            }

            // xay dung title dua tren loai giao dich
            String title = buildNotificationTitle(event.getType(), event.getStatus());

            // tao notification event
            NotificationEvent notificationEvent = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(getNotificationRecipient(event))
                    .title(title)
                    .message(message)
                    .transactionId(event.getTransactionId())
                    .type(notificationType.name())
                    .priority(priority.name())
                    .timestamp(LocalDateTime.now())
                    .build();

            // gui event to kafka
//            notificationEventKafkaTemplate.send("elevate.notifications", notificationEvent.getEventId(), notificationEvent);
            kafkaEventSender.sendWithRetry("elevate.notifications", notificationEvent.getEventId(), notificationEvent);
            log.info("Notification event sent: {} - {}", notificationEvent.getEventId(), notificationEvent);

        } catch (Exception e) {
            log.error("Error sending notification event: {} -  {}", event.getTransactionId(), e.getMessage());
        }
    }

    private String buildNotificationTitle(TransactionType type, TransactionStatus status) {
        StringBuilder title = new StringBuilder();
        switch (type) {
            case TRANSFER:
                title.append("Money Transfer ");
                break;
            case DEPOSIT:
                title.append("Deposit ");
                break;
            case WITHDRAWAL:
                title.append("Withdrawal ");
                break;
        }

        switch (status) {
            case COMPLETED:
                title.append("Successful");
                break;
            case FAILED:
                title.append("Failed");
                break;
            case PENDING:
                title.append("Initiated");
                break;
            default:
                title.append(status.name());
        }
        return title.toString();
    }

    private String getUserIdFromTransaction(Transaction transaction) {
        // lay userid cua nguoi can nhan notification
        // voi giao dich transfer, ca sender va receiver deu nhan notification
        if (transaction.getType() == TransactionType.TRANSFER) {
            // simple by the way send to sender
            return transaction.getFromAccount().getUser().getId();
        } else if (transaction.getType() == TransactionType.DEPOSIT) {
            return transaction.getToAccount().getUser().getId();
        } else {
            return transaction.getFromAccount().getUser().getId();
        }
    }

    // process event
    private void handleRetryableError(TransactionEvent event, Exception e, Acknowledgment ack) {
        log.warn("Retryable error processing event: {} - {}", event.getTransactionId(), e.getMessage());
        event.incrementRetryCount();
        event.addProcessStep("RETRY_INITIATED: " + e.getMessage());

        if (event.getRetryCount() < MAX_RETRY_ATTEMPTS) {
            // TODO: send to retry topic
            long backoffIntervel = calculateBackoffInterval(event.getRetryCount());
            event.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffIntervel));
            sendToRetryTopic(event);
        } else {
            // TODO: send to DLQ
            sendToDLQ(event, "Max retries exceeded");
        }
        ack.acknowledge();

    }

    private void handleNonRetryableError(TransactionEvent event, Exception e, Acknowledgment ack) {
        log.error("Non-retryable error processing event: {} - {}", event.getTransactionId(), e.getMessage());
        event.setStatus(TransactionStatus.FAILED);
        event.addProcessStep("ERROR: " + e.getMessage());
        // TODO: send to DLQ
        sendToDLQ(event, "Non-retryable error" + e.getMessage());
        updateTransactionStatus(event.getTransactionId(), TransactionStatus.FAILED);
        sendFailureNotification(event);
        ack.acknowledge();
    }

    private void sendToRetryTopic(TransactionEvent event) {
        try {
//            kafkaTemplate.send(RETRY_TOPIC, event.getTransactionId(), event).whenComplete((result, ex) -> {
//                if (ex != null) {
//                    log.error("Error sending retry topic: {} - {}", event.getTransactionId(), ex.getMessage());
//                    // TODO: send to DLQ
//                    sendToDLQ(event, "Failed to send to retry topic");
//                }
//            });
            kafkaEventSender.sendWithRetry(RETRY_TOPIC, event.getTransactionId(), event);
        } catch (Exception e) {
            log.error("Error sending event to retry topic: {} - {}", event.getTransactionId(), e.getMessage());
            // TODO: send to DLQ
            sendToDLQ(event, "Error sending to retry topic");
        }
    }

    private void sendToDLQ(TransactionEvent event, String reason) {
        try {
            event.addProcessStep("SENT_TO_DLQ: " + reason);
//            kafkaTemplate.send(DLQ_TOPIC, event.getTransactionId(), event).whenComplete((result, ex) -> {
//                if (ex != null) {
//                    log.error("Error sending DLQ: {} - {}", event.getTransactionId(), ex.getMessage());
//                }
//            });
            kafkaEventSender.sendWithRetry(DLQ_TOPIC, event.getTransactionId(), event);
            // TODO: Update transaction status to FAILED
            updateTransactionStatus(event.getTransactionId(), TransactionStatus.FAILED);
            // TODO: Send notification event
            sendFailureNotification(event);
        } catch (Exception e) {
            log.error("Error sending event to DLQ: {} - {}", event.getTransactionId(), e.getMessage());
        }
    }

    private long calculateBackoffInterval(int retryCount) {
        return (long) Math.pow(2, retryCount) * 1000L; // exponential backoff in seconds: 2^retryCount * 1000
    }


    private void updateTransactionStatus(String transactionId, TransactionStatus status) {
        try {
            Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
            transaction.setStatus(status);
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Error updating transaction status: {} - {}", transactionId, e.getMessage());
        }
    }

    private void sendFailureNotification(TransactionEvent event) {
        String message = buildFailureMessage(event);

        NotificationEvent notification = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(getNotificationRecipient(event))
                .title(buildFailureTitle(event))
                .message(message)
                .type(NotificationEvent.NotificationType.TRANSACTION_FAILED.name())
                .priority(event.isRetryable() ? NotificationEvent.Priority.MEDIUM.name() : NotificationEvent.Priority.HIGH.name())
                .metadata(event.getMetadata())
                .timestamp(LocalDateTime.now())
                .build();

//        notificationEventKafkaTemplate.send("elevate.notifications", notification.getEventId(), notification);
        kafkaEventSender.sendWithRetry("elevate.notifications", notification.getEventId(), notification);
    }

    private String buildFailureMessage(TransactionEvent event) {
        String message = String.format("Transaction %s (%s) of amount %s failed.",
                event.getTransactionId(),
                event.getType(),
                event.getAmount()
        );

        if (event.getErrorMessage() != null) {
            message += " Reason: " + event.getErrorMessage();
        }
        return message;
    }

    private String getNotificationRecipient(TransactionEvent event) {
        // giao dich transfer, ca nguoi gui va nguoi nhan deu nhan notification
        if (event.getType() == TransactionType.TRANSFER) {
//            return event.getFromAccount().getAccountId(); // mac dinh gui cho nguoi chuyen

            // get user id from getUserIdFromTransaction
            return getUserIdFromTransaction(transactionRepository.findById(event.getTransactionId()).get());
        } else if (event.getType() == TransactionType.DEPOSIT) {
            return event.getToAccount().getAccountId();
            // get user id from getUserIdFromTransaction
//            return getUserIdFromTransaction(transactionRepository.findById(event.getTransactionId()).get());
        } else { // withdrawal
            return event.getFromAccount().getAccountId();
        }
    }

    private String buildFailureTitle(TransactionEvent event) {
        StringBuilder title = new StringBuilder();

        switch (event.getType()) {
            case TRANSFER:
                title.append("Transfer Failed");
                break;
            case DEPOSIT:
                title.append("Deposit Failed");
                break;
            case WITHDRAWAL:
                title.append("Withdrawal Failed");
                break;
            default:
                title.append("Transaction Failed");
        }
        return title.toString();
    }

    private String buildCompletedMessage(TransactionEvent event) {
        StringBuilder message = new StringBuilder();
        switch (event.getType()) {
            case TRANSFER:
                message.append(String.format("Transfer of %s from account %s to account %s completed successfully.",
                        event.getAmount(),
                        event.getFromAccount().getAccountNumber(),
                        event.getToAccount().getAccountNumber()
                ));
                break;
            case DEPOSIT:
                message.append(String.format("Deposit of %s to account %s completed successfully.",
                        event.getAmount(),
                        event.getToAccount().getAccountNumber()
                ));
                break;
            case WITHDRAWAL:
                message.append(String.format("Withdrawal of %s from account %s completed successfully.",
                        event.getAmount(),
                        event.getFromAccount().getAccountNumber()
                ));
                break;
            default:
                message.append(String.format("Transaction of %s completed successfully.", event.getAmount()));
        }
        if (event.getType() == TransactionType.TRANSFER || event.getType() == TransactionType.WITHDRAWAL) {
            if (event.getFromAccount() != null && event.getFromAccount().getBalanceAfter() != null) {
                message.append(String.format(" New balance: %s", event.getFromAccount().getBalanceAfter()));
            }
        } else if (event.getType() == TransactionType.DEPOSIT) {
            if (event.getToAccount() != null && event.getToAccount().getBalanceAfter() != null) {
                message.append(String.format(" New balance: %s", event.getToAccount().getBalanceAfter()));
            }
        }
        return message.toString();
    }


}
