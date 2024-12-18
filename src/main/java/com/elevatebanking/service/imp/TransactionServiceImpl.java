package com.elevatebanking.service.imp;

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
import com.elevatebanking.service.nonImp.TransactionValidationService;
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

        transaction.setStatus(TransactionStatus.PENDING);
        Transaction savedTransaction = transactionRepository.save(transaction);
        publishTransactionEvent(savedTransaction, "transaction.initiated");
        return savedTransaction;
    }

    @Override
    public Transaction processTransfer(String fromAccountId, String toAccountId, BigDecimal amount,
            String description) {
        Transaction transaction = null;
        try {
            // Validate accounts and balance before creating transaction

            Account fromAccount = accountService.getAccountById(fromAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
            Account toAccount = accountService.getAccountById(toAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

            validationService.validateTransferTransaction(fromAccount, toAccount, amount);

            // Create transaction after successful validation
            transaction = new Transaction();
            transaction.setFromAccount(accountService.getAccountById(fromAccountId).get());
            transaction.setToAccount(accountService.getAccountById(toAccountId).get());
            transaction.setAmount(amount);
            transaction.setType(TransactionType.TRANSFER);
            transaction.setDescription(description);
            transaction.setStatus(TransactionStatus.PENDING);

            // Save transaction
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.initiated");

            try {
                // Execute money transfer
                BigDecimal fromBalance = accountService.getBalance(fromAccountId).subtract(amount);
                BigDecimal toBalance = accountService.getBalance(toAccountId).add(amount);

                accountService.updateBalance(fromAccountId, fromBalance);
                accountService.updateBalance(toAccountId, toBalance);

                // Update successful status
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction = transactionRepository.save(transaction);
                publishTransactionEvent(transaction, "transaction.completed");

                return transaction;
            } catch (Exception e) {
                // Handle error during transaction execution
                if (transaction != null) {
                    transaction.setStatus(TransactionStatus.FAILED);
                    transaction = transactionRepository.save(transaction);
                    publishTransactionEvent(transaction, "transaction.failed");
                }
                throw new RuntimeException("Error executing transfer", e);
            }
        } catch (IllegalArgumentException e) {
            // Handle validation error
            log.error("Validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Handle other errors
            log.error("Error processing transfer: {}", e.getMessage());
            throw new RuntimeException("Error processing transfer", e);
        }
    }

    @Override
    public Transaction processDeposit(String accountId, BigDecimal amount) {

        Account account = accountService.getAccountById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // validateTransactionAmount(amount);
        validationService.validateDepositTransaction(account, amount);
        Transaction transaction = null;
        try {
            transaction = new Transaction();
            transaction.setToAccount(accountService.getAccountById(accountId).get());
            transaction.setAmount(amount);
            transaction.setType(TransactionType.DEPOSIT);
            transaction.setStatus(TransactionStatus.PENDING);

            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.initiated");

            BigDecimal newBalance = accountService.getBalance(accountId).add(amount);
            accountService.updateBalance(accountId, newBalance);

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");
            return transaction;
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
    public Transaction processWithdrawal(String accountId, BigDecimal amount) {

        Account account = accountService.getAccountById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        validationService.validateWithdrawalTransaction(account, amount);

        Transaction transaction = null;
        try {
            transaction = new Transaction();
            transaction.setFromAccount(accountService.getAccountById(accountId).get());
            transaction.setAmount(amount);
            transaction.setType(TransactionType.WITHDRAWAL);
            transaction.setStatus(TransactionStatus.PENDING);

            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.initiated");

            BigDecimal newBalance = accountService.getBalance(accountId).subtract(amount);
            accountService.updateBalance(accountId, newBalance);

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "transaction.completed");
            return transaction;
        } catch (Exception e) {
            log.error("Error processing withdrawal: {}", e.getMessage());
            if (transaction != null) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction = transactionRepository.save(transaction);
                publishTransactionEvent(transaction, "transaction.failed");
            }
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

    // private void validateDailyLimit(String accountId, BigDecimal amount) {
    // if (accountId == null || amount == null) {
    // return;
    // }
    // LocalDateTime startOfDay =
    // LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
    // List<Transaction> dailyTransactions =
    // transactionRepository.findTransactionsByAccountAndDateRange(accountId,
    // startOfDay, LocalDateTime.now());

    // /*
    // * dailyTransactions.stream():
    // * Chuyển danh sách giao dịch thành một stream để xử lý dữ liệu
    // * .filter(t -> t.getStatus() == TransactionStatus.COMPLETED):
    // * Lọc ra chỉ những giao dịch có trạng thái COMPLETED
    // * Loại bỏ các giao dịch có trạng thái khác (như PENDING, FAILED...)
    // * .map(Transaction::getAmount):
    // * Chuyển đổi mỗi giao dịch thành số tiền của giao dịch đó
    // * Sử dụng method reference để lấy giá trị amount từ mỗi Transaction
    // * .reduce(BigDecimal.ZERO, BigDecimal::add):
    // * Khởi đầu với giá trị 0 (BigDecimal.ZERO)
    // * Cộng dồn tất cả các số tiền lại với nhau
    // * Sử dụng method reference BigDecimal::add để thực hiện phép cộng
    // */
    // BigDecimal dailyTotal = dailyTransactions.stream()
    // .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
    // .map(Transaction::getAmount)
    // .reduce(BigDecimal.ZERO, BigDecimal::add);

    // if (dailyTotal.add(amount).compareTo(DAILY_TRANSFER_LIMIT) > 0) {
    // throw new InvalidOperationException("Daily transfer limit exceeded");
    // }
    // }

    private void publishTransactionEvent(Transaction transaction, String eventType) {
        try {
            TransactionEvent event = new TransactionEvent(transaction);
            kafkaTemplate.send("elevate.transactions", eventType, event);
            //
        } catch (Exception e) {
            log.error("Error publishing transaction event: {}", e.getMessage());
        }
    }

    //// NEW SERVICE

    @Override
    public TransactionResponse transfer(TransferRequest request) {
        log.info("Processing transfer request: {} -> {}, amount: {}", request.getFromAccountId(),
                request.getToAccountId(), request.getAmount());

        Account fromAccount = accountService.getAccountById(request.getFromAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        Account toAccount = accountService.getAccountById(request.getToAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

        // validate account and balances
        validationService.validateTransferTransaction(fromAccount, toAccount, request.getAmount());

        // create transaction and save
        Transaction transaction = new Transaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setDescription(request.getDescription());
        transaction.setStatus(TransactionStatus.PENDING);

        transaction = transactionRepository.save(transaction);

        // publish transaction event
        publishTransactionEvent(transaction, "transaction.initiated");

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
