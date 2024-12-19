package com.elevatebanking.service.processor;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.NotificationEvent;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProcessor {
    private final ITransactionService transactionService;
    private final IAccountService accountService;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final KafkaTemplate<String, NotificationEvent> notificationEventKafkaTemplate;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    @KafkaListener(
            topics = "elevate.transaction",
            groupId = "transaction-processor",
            containerFactory = "transactionKafkaListenerContainerFactory"
    )
    public void processTransactionEvent(TransactionEvent event, Acknowledgment ack) {
        log.info("Processing transaction event: {}", event);
        try {
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
        } catch (Exception e) {
            log.error("Error processing transaction event: {}", event, e);
            handleProcessingError(event, e, ack);
        }
    }

    @KafkaListener(
            topics = "elevate.transaction.retry",
            groupId = "transaction-processor-retry",
            containerFactory = "transactionKafkaListenerContainerFactory"
    )
    public void processRetryEvent(TransactionEvent event, Acknowledgment ack) {
        log.info("Processing retry event: {}", event);
        try {
            // increate retry count
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() <= MAX_RETRY_ATTEMPTS) {
                processTransactionEvent(event, ack);
            } else {
                kafkaTemplate.send("elevate.transaction.dlq", event);
                ack.acknowledge();
            }

        } catch (Exception e) {
            log.error("Error processing retry event: {}", event, e);
            handleProcessingError(event, e, ack);
            ///
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
                    break;
                case DEPOSIT:
                    break;
                case WITHDRAWAL:
                    break;
                default:
                    log.error("Unknown transaction type: {}", transaction.getType());
                    throw new InvalidOperationException("Unknown transaction type: " + transaction.getType());
            }
        } catch (Exception e) {
            log.error("Error handling transaction initiated event: {}", e.getMessage());
            event.setStatus(TransactionStatus.FAILED);
            kafkaTemplate.send("elevate.transaction", "transaction.failed", event);
        }

    }

    private void handleTransactionCompleted(TransactionEvent event) {
        log.info("Handling transaction completed event: {}", event.getTransactionId());
    }

    private void handleTransactionFailed(TransactionEvent event) {
        log.info("Handling transaction failed event: {}", event.getTransactionId());
    }

    private void handleProcessingError(TransactionEvent event, Exception e, Acknowledgment ack) {
        if (event.getRetryCount() < MAX_RETRY_ATTEMPTS) {
            kafkaTemplate.send("elevate.transactions.retry", event);
        } else {
            kafkaTemplate.send("elevate.transactions.dlq", event);
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
        kafkaTemplate.send("elevate.transactions", "transaction.completed", event);
    }

    private void processDepositTransaction(Transaction transaction, TransactionEvent event) {
        Account account = accountService.getAccountById(transaction.getToAccount().getId()).get();
        BigDecimal newBalance = account.getBalance().add(transaction.getAmount());

        accountService.updateBalance(account.getId(), newBalance);

        event.setStatus(TransactionStatus.COMPLETED);
        event.updateBalances(null, newBalance);

        kafkaTemplate.send("elevate.transactions", "transaction.completed", event);
    }

    private void processWithdrawalTransaction(Transaction transaction, TransactionEvent event) {
        Account account = accountService.getAccountById(transaction.getFromAccount().getId()).get();
        BigDecimal newBalance = account.getBalance().subtract(transaction.getAmount());

        accountService.updateBalance(account.getId(), newBalance);

        event.setStatus(TransactionStatus.COMPLETED);
        event.updateBalances(newBalance, null);

        kafkaTemplate.send("elevate.transactions", "transaction.completed", event);
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
            sendNotificationEvent(transaction, "CRITICAL: Manual rollback investigation required");
        }
    }

    private void sendNotificationEvent(Transaction transaction, String message) {

        log.info("Sending notification event: {} {}", transaction.getId(), message);
        try {
            // xac dinh loai thong bao va priority dua tren transaction status
            NotificationEvent.NotificationType notificationType;
            NotificationEvent.Priority priority;

            switch (transaction.getStatus()) {
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
            String title = buildNotificationTitle(transaction);

            // tao notification event
            NotificationEvent notificationEvent = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(getUserId(transaction))
                    .title(title)
                    .message(message)
                    .transactionId(transaction.getId())
                    .type(notificationType.name())
                    .priority(priority.name())
                    .timestamp(LocalDateTime.now())
                    .build();

            // gui event to kafka
            notificationEventKafkaTemplate.send("elevate.notifications", notificationEvent.getEventId(), notificationEvent);
            log.info("Notification event sent: {} - {}", notificationEvent.getEventId(), notificationEvent);

        } catch (Exception e) {
            log.error("Error sending notification event: {} -  {}", transaction.getId(), e.getMessage());
        }
    }

    private String buildNotificationTitle(Transaction transaction) {
        StringBuilder title = new StringBuilder();
        switch (transaction.getType()) {
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

        switch (transaction.getStatus()) {
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
                title.append(transaction.getStatus().name());
        }
        return title.toString();
    }

    private String getUserId(Transaction transaction) {
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
}
