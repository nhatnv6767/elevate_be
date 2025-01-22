package com.elevatebanking.service.transaction;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.exception.*;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import com.elevatebanking.service.transaction.config.TransactionLockManager;
import com.elevatebanking.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionRepository transactionRepository;
    private final IAccountService accountService;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final TransactionValidationService validationService;
    private final TransactionCompensationService compensationService;
    private final TransactionMonitoringService monitoringService;
    private final TransactionRecoveryService recoveryService;
    private final SecurityUtils securityUtils;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public Transaction createTransaction(Transaction transaction) throws InterruptedException {
        validationService.validateTransferTransaction(transaction.getFromAccount(), transaction.getToAccount(),
                transaction.getAmount());
        return initializeAndSaveTransaction(transaction);
    }

    private Transaction initializeAndSaveTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.PENDING);
        Transaction savedTransaction = transactionRepository.save(transaction);
        publishTransactionEvent(savedTransaction, "transaction.initiated");
        return savedTransaction;
    }

    private Transaction buildTransaction(Account fromAccount, Account toAccount, BigDecimal amount, String description,
                                         TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setType(type);
        transaction.setStatus(TransactionStatus.PENDING);
        return transaction;
    }

    private void executeTransfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        try {
            BigDecimal fromBalance = accountService.getBalance(fromAccountId).subtract(amount);
            BigDecimal toBalance = accountService.getBalance(toAccountId).add(amount);

            accountService.updateBalance(fromAccountId, fromBalance);
            accountService.updateBalance(toAccountId, toBalance);
        } catch (Exception e) {
            log.error("Error executing transfer: {}", e.getMessage());
            throw new TransactionProcessingException("Error executing transfer", null, true);
        }

    }

    private void executeWithdrawal(String accountId, BigDecimal amount) {
        BigDecimal newBalance = accountService.getBalance(accountId).subtract(amount);
        accountService.updateBalance(accountId, newBalance);
    }

    private void executeDeposit(String accountId, BigDecimal amount) {
        BigDecimal newBalance = accountService.getBalance(accountId).add(amount);
        accountService.updateBalance(accountId, newBalance);
    }

    private void handleTransactionError(Transaction transaction, Exception e) {
        // log.error("Transaction failed: {}", transaction.getId(), e);
        // transaction.setStatus(TransactionStatus.FAILED);
        // transaction = transactionRepository.save(transaction);
        // publishTransactionEvent(transaction, "transaction.failed");

        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            CompletableFuture.runAsync(() -> {
                publishTransactionEvent(transaction, "transaction.failed");
                monitoringService.sendAlertNotification("Transaction failed: " + e.getMessage());
            });
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    // TODO: who calls this method?
    public Transaction processTransfer(String fromAccountId, String toAccountId, BigDecimal amount,
                                       String description) {
        Transaction transaction = null;
        try {
            Account fromAccount = accountService.getAccountById(fromAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
            Account toAccount = accountService.getAccountById(toAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
            validationService.validateTransferTransaction(fromAccount, toAccount, amount);
            transaction = createInitialTransaction(fromAccountId, toAccountId, amount, description,
                    TransactionType.TRANSFER);
            executeTransfer(fromAccountId, toAccountId, amount);
            //
            return completeTransaction(transaction);
        } catch (Exception e) {
            log.error("Transfer failed to transaction {}", e.getMessage());

            if (transaction != null) {
                compensationService.compensateTransaction(
                        transaction, "Error executing transfer: " + e.getMessage());

                transaction.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(transaction);

                publishTransactionEvent(transaction, "transaction.failed");
            }

            throw new TransactionProcessingException(
                    "Error processing transfer: " + e.getMessage(),
                    transaction != null ? transaction.getId() : null,
                    true);
        }

    }

    private Transaction createInitialTransaction(String fromAccountId, String toAccountId, BigDecimal amount,
                                                 String description, TransactionType type) {
        // Validate accounts and balance before creating transaction
        Transaction transaction = new Transaction();
        transaction.setFromAccount(validateAndGetAccount(fromAccountId, "Source account not found"));
        transaction.setToAccount(validateAndGetAccount(toAccountId, "Destination account not found"));
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setDescription(description);
        transaction.setStatus(TransactionStatus.PENDING);

        transaction = transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.initiated");
        return transaction;
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transaction processDeposit(String accountId, BigDecimal amount) throws InterruptedException {

        Account account = validateAndGetAccount(accountId, "Account not found");
        // validateTransactionAmount(amount);
        validationService.validateDepositTransaction(account, amount);

        Transaction transaction = buildTransaction(null, account, amount, "Deposit", TransactionType.DEPOSIT);
        transaction = initializeAndSaveTransaction(transaction);
        try {
            executeDeposit(accountId, amount);
            return completeTransaction(transaction);
        } catch (Exception e) {
            handleTransactionError(transaction, "Error executing deposit", e);
            throw new RuntimeException("Error executing deposit", e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transaction processWithdrawal(String accountId, BigDecimal amount) throws InterruptedException {

        Account account = validateAndGetAccount(accountId, "Account not found");
        validationService.validateWithdrawalTransaction(account, amount);

        Transaction transaction = buildTransaction(account, null, amount, "Withdrawal", TransactionType.WITHDRAWAL);
        try {
            executeWithdrawal(accountId, amount);
            return completeTransaction(transaction);
        } catch (Exception e) {
            handleTransactionError(transaction, "Error executing withdrawal", e);
            throw new RuntimeException("Error processing withdrawal");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> getTransactionById(String id) {
        return transactionRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByAccountId(String accountId) {
        return transactionRepository.findTransactionsByAccountId(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getPendingTransactions() {
        LocalDateTime timout = LocalDateTime.now().minusMinutes(15);
        return transactionRepository.findPendingTransactionsOlderThan(timout);
    }

    @Override
    public void cancelTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidOperationException("Only pending transactions can be cancelled");
        }
        transaction.setStatus(TransactionStatus.CANCELLED);
        transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.cancelled");
    }

    private void publishTransactionEvent(Transaction transaction, String eventType) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type cannot be null or empty");
        }


        try {
            TransactionEvent event = new TransactionEvent(transaction, eventType);
            if (transaction.getStatus() == TransactionStatus.COMPLETED ||
                    transaction.getStatus() == TransactionStatus.FAILED) {

                // Thêm deduplication key để tránh gửi trùng
                String deduplicationKey = "notification:" + transaction.getId() + ":" + transaction.getStatus();
                Boolean isFirstNotification = redisTemplate.opsForValue()
                        .setIfAbsent(deduplicationKey, "1", 3, TimeUnit.MINUTES);

                if (Boolean.TRUE.equals(isFirstNotification)) {
                    kafkaTemplate.send("elevate.transactions", eventType, event)
                            .thenAccept(result -> log.info("Transaction event sent successfully: {}",
                                    event.getTransactionId()))
                            .exceptionally(ex -> {
                                log.error("Failed to send transaction event: {}", ex.getMessage());
                                return null; // Không throw exception để tránh rollback transaction
                            });
                } else {
                    log.info("Duplicate notification prevented for transaction: {}", transaction.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error publishing transaction event: {}", e.getMessage());
            // throw new RuntimeException("Failed to publish transaction event", e);
        }
    }

    private Transaction createInitialTransaction(Account fromAccount, Account toAccount, BigDecimal amount,
                                                 TransactionType type, String description) {
        Transaction transaction = new Transaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setDescription(description);
        transaction.setStatus(TransactionStatus.PENDING);

        transaction = transactionRepository.save(transaction);

        // publish transaction event
        // publishTransactionEvent(transaction, "transaction.initiated");

        return transaction;
    }

    private Account validateAndGetAccount(String accountId, String errorMessage) {
        return accountService.getAccountById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(errorMessage));
    }

    private Transaction completeTransaction(Transaction transaction) {
        log.debug("Completing transaction: {}", transaction.getId());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.completed");
        log.info("Transaction completed successfully: {}", transaction.getId());
        return transaction;
    }

    private void handleTransactionError(Transaction transaction, String errorMessage, Exception e) {
        log.error("{}: {}", errorMessage, e.getMessage());
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.failed");

    }

    private boolean needsRollback(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.FAILED &&
                (transaction.getType() == TransactionType.TRANSFER ||
                        transaction.getType() == TransactionType.WITHDRAWAL);
    }

    private void performRollback(Transaction transaction) {
        log.info("Performing rollback for transaction: {}", transaction.getId());
        // add rollback logic here
        try {
            transaction = transactionRepository.findById(transaction.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
            switch (transaction.getType()) {
                case TRANSFER:
                    executeTransfer(
                            transaction.getToAccount().getId(), transaction.getFromAccount().getId(),
                            transaction.getAmount());
                    break;
                case WITHDRAWAL:
                    executeDeposit(transaction.getFromAccount().getId(), transaction.getAmount());
                    break;
                // case DEPOSIT:
                // executeWithdrawal(transaction.getToAccount().getId(),
                // transaction.getAmount());
                // break;
                default:
                    break;
            }
            transaction.setStatus(TransactionStatus.ROLLED_BACK);
            transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.rolled_back");
        } catch (Exception e) {
            log.error("Error performing rollback: {}", e.getMessage());
            transaction.setStatus(TransactionStatus.ROLLBACK_FAILED);
            transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.rollback_failed");
            throw new TransactionRollbackException("Error performing rollback", transaction.getId());
        }
    }

    //// NEW SERVICE

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public TransactionResponse transfer(TransferRequest request) throws InterruptedException {
        log.info("Processing transfer request: {} -> {}, amount: {}", request.getFromAccountNumber(),
                request.getToAccountNumber(), request.getAmount());
        try {

            // 1. Validate and get accounts
            Account fromAccount = accountService.getAccountByNumber(request.getFromAccountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
            Account toAccount = accountService.getAccountByNumber(request.getToAccountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

            // validate using account IDs
            validationService.validateTransferTransaction(
                    accountService.getAccountById(fromAccount.getId()).orElseThrow(),
                    accountService.getAccountById(toAccount.getId()).orElseThrow(),
                    request.getAmount());

            // 2. initialize transaction without publish event
            Transaction transaction = createInitialTransaction(fromAccount, toAccount, request.getAmount(),
                    TransactionType.TRANSFER, request.getDescription());

            // 3. execute transfer with retry mechanism
            executeTransferWithRetry(transaction);
            return mapToTransactionResponse(transaction);

        } catch (Exception e) {
            log.error("Error processing transfer: {}", e.getMessage());
            throw e;
        }

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void executeTransferWithRetry(Transaction transaction) {
        try {
            Transaction currentTx = transactionRepository.findByIdForUpdate(transaction.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

            if (currentTx.getStatus() != TransactionStatus.PENDING) {
                throw new InvalidOperationException("Transaction is not pending");
            }

            executeTransfer(transaction.getFromAccount().getId(),
                    transaction.getToAccount().getId(),
                    transaction.getAmount());

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");
        } catch (DataAccessException e) {
            throw new RetryableException("Database error", e);
            // log.error("Error executing transfer: {}", e.getMessage());
            // throw new TransactionProcessingException("Error executing transfer",
            // transaction.getId(), true);
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.failed");
            compensationService.compensateTransaction(transaction, "Error executing transfer: " + e.getMessage());
            throw new NonRetryableException("Non-retryable error", e);
        }
    }

    private Transaction initializeTransaction(Account fromAccount, Account toAccount, TransferRequest request) {
        Transaction transaction = new Transaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setDescription(request.getDescription());
        transaction.setStatus(TransactionStatus.PENDING);

        transaction = transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.initiated");
        return transaction;
    }

    @Override
    public TransactionResponse deposit(DepositRequest request) throws InterruptedException {
        log.info("Processing deposit request: account {}, amount: {}", request.getAccountNumber(), request.getAmount());

        Account account = accountService.getAccountByNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        // accountService.validateAccount(account.getId(), null);
        validationService.validateDepositTransaction(account, request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setToAccount(account);
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setDescription(request.getDescription());
        transaction.setStatus(TransactionStatus.PENDING);

        transaction = transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.initiated");

        try {
//            processDeposit(account.getId(), request.getAmount());
            BigDecimal newBalance = account.getBalance().add(request.getAmount());
            accountService.updateBalance(account.getId(), newBalance);

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");
            return mapToTransactionResponse(transaction);
        } catch (Exception e) {
            log.error("Error processing deposit: {}", e.getMessage());
            if (transaction != null) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction = transactionRepository.save(transaction);
                publishTransactionEvent(transaction, "transaction.failed");
            }
            throw new RuntimeException("Error processing deposit");
        }

    }

    @Override
    public WithdrawalResponse withdraw(WithdrawRequest request) throws InterruptedException {

        log.info("Processing withdrawal request: account {}, amount: {}", request.getAccountNumber(),
                request.getAmount());
        // validateTransactionAmount(request.getAmount());

        Account account = accountService.getAccountByNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        validationService.validateWithdrawalTransaction(account, request.getAmount());
        Transaction transaction = createWithdrawalTransaction(account, request);

        try {
            // processWithdrawal(account.getId(), request.getAmount());
            executeWithdrawal(account.getId(), request.getAmount());
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");
            return mapToWithdrawalResponse(transaction);

        } catch (Exception e) {
            handleTransactionError(transaction, e);
            throw e;
        }
    }

    private Transaction createWithdrawalTransaction(Account account, WithdrawRequest request) {
        Transaction transaction = new Transaction();
        transaction.setFromAccount(account);
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.WITHDRAWAL);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setAtmId(request.getAtmId());
        transaction.setDescription(request.getDescription());
        transaction.setDispensedDenominations(request.getRequestedDenominations());

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return mapToTransactionResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getTransactionHistory(String accountId, LocalDateTime startDate,
                                                                  LocalDateTime endDate, Pageable pageable) {
        startDate = startDate != null ? startDate : LocalDateTime.now().minusMonths(1);
        endDate = endDate != null ? endDate : LocalDateTime.now();

        log.debug("Fetching transaction history for account: {}, start date: {}, end date: {}", accountId, startDate,
                endDate);

        Page<Transaction> transactions = transactionRepository.findTransactionsByAccountAndDateRange(accountId,
                startDate, endDate, pageable);

        if (transactions == null) {
            log.error("Null response from repository for account: {}, start date: {}, end date: {}", accountId,
                    startDate, endDate);
            throw new RuntimeException("Error fetching transaction history");
        }

        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException("Start date cannot be after end date");
        }

        if (ChronoUnit.MONTHS.between(startDate, endDate) > 12) {
            throw new InvalidOperationException("Date range cannot exceed 12 months");
        }

        return transactions.map(transaction -> {
            try {
                return mapToTransactionHistoryResponse(transaction);
            } catch (Exception e) {
                log.error("Error mapping transaction: {} - {}", transaction.getId(), e.getMessage());
                throw new RuntimeException("Error mapping transaction", e);
            }

        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getUserTransactions(String userId, LocalDateTime startDate, LocalDateTime endDate, TransactionType type, Pageable page) {
        startDate = startDate != null ? startDate : LocalDateTime.now().minusMonths(1);
        endDate = endDate != null ? endDate : LocalDateTime.now();

        log.debug("Fetching user transactions: userId={}, startDate={}, endDate={}, type={}",
                userId, startDate, endDate, type);

        Page<Transaction> transactions = transactionRepository.findUserTransactions(
                userId, startDate, endDate, type, page);

        if (startDate.isAfter(endDate)) {
            throw new InvalidOperationException("Start date cannot be after end date");
        }

        if (ChronoUnit.MONTHS.between(startDate, endDate) > 12) {
            throw new InvalidOperationException("Date range cannot exceed 12 months");
        }

        return transactions.map(transaction -> {
            try {
                return mapToTransactionHistoryResponse(transaction);
            } catch (Exception e) {
                log.error("Error mapping transaction: {} - {}", transaction.getId(), e.getMessage());
                throw new RuntimeException("Error mapping transaction", e);
            }
        });
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getId())
                .type(transaction.getType() != null ? transaction.getType().name() : null)
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .fromAccount(transaction.getFromAccount() != null ?
                        transaction.getFromAccount().getAccountNumber() : null)
                .toAccount(transaction.getToAccount() != null ?
                        transaction.getToAccount().getAccountNumber() : null)
                .description(transaction.getDescription())
                .timestamp(transaction.getCreatedAt())
                .build();
    }

    private WithdrawalResponse mapToWithdrawalResponse(Transaction transaction) {
        return WithdrawalResponse.builder()
                .transactionId(transaction.getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .fromAccount(
                        transaction.getFromAccount() != null ? transaction.getFromAccount().getAccountNumber() : null)
                .description(transaction.getDescription())
                .timestamp(transaction.getCreatedAt())
                .atmId(transaction.getAtmId())
                .dispensedDenominations(transaction.getDispensedDenominations())
                .build();
    }

    private TransactionHistoryResponse mapToTransactionHistoryResponse(Transaction transaction) {
        TransactionHistoryResponse.TransactionParty fromParty = null;
        TransactionHistoryResponse.TransactionParty toParty = null;

        if (transaction.getFromAccount() != null) {
            fromParty = TransactionHistoryResponse.TransactionParty.builder()
                    .accountId(transaction.getFromAccount().getId())
                    .accountNumber(transaction.getFromAccount().getAccountNumber())
                    .accountName(transaction.getFromAccount().getUser() != null
                            ? transaction.getFromAccount().getUser().getFullName()
                            : null)
                    .balanceAfter(transaction.getFromAccount().getBalance())
                    .build();
        }

        if (transaction.getToAccount() != null) {
            toParty = TransactionHistoryResponse.TransactionParty.builder()
                    .accountId(transaction.getToAccount().getId())
                    .accountNumber(transaction.getToAccount().getAccountNumber())
                    .accountName(transaction.getToAccount().getUser() != null
                            ? transaction.getToAccount().getUser().getFullName()
                            : null)
                    .balanceAfter(transaction.getToAccount().getBalance())
                    .build();
        }

        return TransactionHistoryResponse.builder()
                .transactionId(transaction.getId())
                .type(transaction.getType() != null ? transaction.getType().name() : null)
                .amount(transaction.getAmount())
                .status(transaction.getStatus() != null ? transaction.getStatus().name() : null)
                .description(transaction.getDescription())
                .timestamp(transaction.getCreatedAt())
                .from(fromParty)
                .to(toParty)
                .build();
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkAndRecoverTransactions() {
        List<Transaction> stuckTransactions = recoveryService.findStuckTransactions();
        for (Transaction transaction : stuckTransactions) {
            try {
                recoveryService.recoverTransaction(transaction);
            } catch (Exception e) {
                log.error("Failed to recover transaction: {}", transaction.getId(), e);
            }
        }
    }

}
