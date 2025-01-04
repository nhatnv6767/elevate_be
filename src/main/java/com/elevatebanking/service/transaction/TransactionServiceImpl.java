package com.elevatebanking.service.transaction;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    @Override
    public Transaction createTransaction(Transaction transaction) {
        // validateTransactionAmount(transaction.getAmount());
        // validateDailyLimit(transaction.getFromAccount().getId(),
        // transaction.getAmount());
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
        BigDecimal fromBalance = accountService.getBalance(fromAccountId).subtract(amount);
        BigDecimal toBalance = accountService.getBalance(toAccountId).add(amount);

        accountService.updateBalance(fromAccountId, fromBalance);
        accountService.updateBalance(toAccountId, toBalance);
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
        log.error("Transaction failed: {}", transaction.getId(), e);
        transaction.setStatus(TransactionStatus.FAILED);
        transaction = transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.failed");
    }

    @Override
    public Transaction processTransfer(String fromAccountId, String toAccountId, BigDecimal amount,
                                       String description) {
        // Validate accounts and balance before creating transaction

        Account fromAccount = validateAndGetAccount(fromAccountId, "Source account not found");
        Account toAccount = validateAndGetAccount(toAccountId, "Destination account not found");
        validationService.validateTransferTransaction(fromAccount, toAccount, amount);

        Transaction transaction = buildTransaction(fromAccount, toAccount, amount, description,
                TransactionType.TRANSFER);
        transaction = initializeAndSaveTransaction(transaction);

        try {
            // Execute money transfer
            executeTransfer(fromAccountId, toAccountId, amount);
            // Update successful status
            return completeTransaction(transaction);
        } catch (Exception e) {
            // Handle error during transaction execution
            handleTransactionError(transaction, "Error executing transfer", e);
            throw new RuntimeException("Error executing transfer", e);
        }

    }

    @Override
    public Transaction processDeposit(String accountId, BigDecimal amount) {

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
    public Transaction processWithdrawal(String accountId, BigDecimal amount) {

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
            kafkaTemplate.send("${spring.kafka.topics.transaction}", eventType, event);
            //
        } catch (Exception e) {
            log.error("Error publishing transaction event: {}", e.getMessage());
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
        publishTransactionEvent(transaction, "transaction.initiated");

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
        }
    }

    //// NEW SERVICE

    @Override
    public TransactionResponse transfer(TransferRequest request) {
        log.info("Processing transfer request: {} -> {}, amount: {}", request.getFromAccountId(),
                request.getToAccountId(), request.getAmount());

        Account fromAccount = validateAndGetAccount(request.getFromAccountId(), "Source account not found");
        Account toAccount = validateAndGetAccount(request.getToAccountId(), "Destination account not found");

        // validate account and balances
        validationService.validateTransferTransaction(fromAccount, toAccount, request.getAmount());

        // create and save initial transaction
        Transaction transaction = createInitialTransaction(
                fromAccount, toAccount, request.getAmount(),
                TransactionType.TRANSFER, request.getDescription());

        try {
            processTransfer(
                    fromAccount.getId(),
                    toAccount.getId(),
                    request.getAmount(),
                    request.getDescription());

            // update transaction status
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");
        } catch (Exception e) {
            log.error("Error processing transfer: {}", e.getMessage());
            if (transaction != null) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction = transactionRepository.save(transaction);
                publishTransactionEvent(transaction, "transaction.failed");
            }
            throw new RuntimeException("Error processing transfer");
        }

        return mapToTransactionResponse(transaction);
    }

    @Override
    public TransactionResponse deposit(DepositRequest request) {
        log.info("Processing deposit request: account {}, amount: {}", request.getAccountId(), request.getAmount());

        // validateTransactionAmount(request.getAmount());
        // if(request.getAmount().compareTo(SINGLE_TRANSFER_LIMIT) > 0){
        // throw new InvalidOperationException("Amount exceeds single transfer limit");
        // }
        Account account = accountService.getAccountById(request.getAccountId())
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
            processDeposit(account.getId(), request.getAmount());
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");
        } catch (Exception e) {
            log.error("Error processing deposit: {}", e.getMessage());
            if (transaction != null) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction = transactionRepository.save(transaction);
                publishTransactionEvent(transaction, "transaction.failed");
            }
            throw new RuntimeException("Error processing deposit");
        }

        return mapToTransactionResponse(transaction);

    }

    @Override
    public TransactionResponse withdraw(WithdrawRequest request) {
        log.info("Processing withdrawal request: account {}, amount: {}", request.getAccountId(), request.getAmount());

        // validateTransactionAmount(request.getAmount());

        Account account = accountService.getAccountById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        // accountService.validateAccount(account.getId(), request.getAmount());
        validationService.validateWithdrawalTransaction(account, request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setFromAccount(account);
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.WITHDRAWAL);
        transaction.setDescription(request.getDescription());
        transaction.setStatus(TransactionStatus.PENDING);

        transaction = transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.initiated");

        try {
            processWithdrawal(account.getId(), request.getAmount());
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");

        } catch (Exception e) {
            log.error("Error processing withdrawal: {}", e.getMessage());
            if (transaction != null) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction = transactionRepository.save(transaction);
                publishTransactionEvent(transaction, "transaction.failed");
            }
            throw new RuntimeException("Error processing withdrawal");
        }
        return mapToTransactionResponse(transaction);
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
    public List<TransactionHistoryResponse> getTransactionHistory(String accountId, LocalDateTime startDate,
                                                                  LocalDateTime endDate) {
        List<Transaction> transactions;
        if (startDate != null && endDate != null) {
            transactions = transactionRepository.findTransactionsByAccountAndDateRange(accountId, startDate, endDate);
        } else {
            transactions = transactionRepository.findTransactionsByAccountId(accountId);
        }

        return transactions.stream()
                .map(this::mapToTransactionHistoryResponse)
                .collect(Collectors.toList());
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .fromAccount(
                        transaction.getFromAccount() != null ? transaction.getFromAccount().getAccountNumber() : null)
                .toAccount(transaction.getToAccount() != null ? transaction.getToAccount().getAccountNumber() : null)
                .description(transaction.getDescription())
                .timestamp(transaction.getCreatedAt())
                .build();
    }

    private TransactionHistoryResponse mapToTransactionHistoryResponse(Transaction transaction) {
        TransactionHistoryResponse.TransactionParty fromParty = null;
        TransactionHistoryResponse.TransactionParty toParty = null;

        if (transaction.getFromAccount() != null) {
            toParty = TransactionHistoryResponse.TransactionParty.builder()
                    .accountId(transaction.getToAccount().getId())
                    .accountNumber(transaction.getToAccount().getAccountNumber())
                    .accountName(transaction.getToAccount().getUser().getFullName())
                    .build();
        }

        return TransactionHistoryResponse.builder()
                .transactionId(transaction.getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .description(transaction.getDescription())
                .timestamp(transaction.getCreatedAt())
                .from(fromParty)
                .to(toParty)
                .build();
    }

}
