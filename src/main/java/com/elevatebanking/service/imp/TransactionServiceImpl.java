package com.elevatebanking.service.imp;

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

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionRepository transactionRepository;
    private final IAccountService accountService;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal(5000000); // 5,000,000$
    private static final BigDecimal SINGLE_TRANSFER_LIMIT = new BigDecimal(1000000); // 1,000,000$


    @Override
    public Transaction createTransaction(Transaction transaction) {
        validateTransactionAmount(transaction.getAmount());
        validateDailyLimit(transaction.getFromAccount().getId(), transaction.getAmount());

        transaction.setStatus(TransactionStatus.PENDING);
        Transaction savedTransaction = transactionRepository.save(transaction);
        publishTransactionEvent(savedTransaction, "transaction.initiated");
        return savedTransaction;
    }

    @Override
    public Transaction processTransfer(String fromAccountId, String toAccountId, BigDecimal amount, String description) {
        Transaction transaction = null;
        try {
            // Validate accounts and balance before creating transaction
            accountService.validateAccount(fromAccountId, amount);
            accountService.validateAccount(toAccountId, null);

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
        validateTransactionAmount(amount);
        accountService.validateAccount(accountId, null);
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
        validateTransactionAmount(amount);
        accountService.validateAccount(accountId, amount);
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
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidOperationException("Only pending transactions can be cancelled");
        }
        transaction.setStatus(TransactionStatus.CANCELLED);
        transactionRepository.save(transaction);
        publishTransactionEvent(transaction, "transaction.cancelled");
    }

    private void validateTransactionAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("Amount must be greater than 0");
        }
        if (amount.compareTo(SINGLE_TRANSFER_LIMIT) > 0) {
            throw new InvalidOperationException("Amount exceeds single transfer limit");
        }
    }

    private void validateDailyLimit(String accountId, BigDecimal amount) {
        if (accountId == null || amount == null) {
            return;
        }
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        List<Transaction> dailyTransactions = transactionRepository.findTransactionsByAccountAndDateRange(accountId, startOfDay, LocalDateTime.now());

        /*
         *  dailyTransactions.stream():
            Chuyển danh sách giao dịch thành một stream để xử lý dữ liệu
            .filter(t -> t.getStatus() == TransactionStatus.COMPLETED):
            Lọc ra chỉ những giao dịch có trạng thái COMPLETED
            Loại bỏ các giao dịch có trạng thái khác (như PENDING, FAILED...)
            .map(Transaction::getAmount):
            Chuyển đổi mỗi giao dịch thành số tiền của giao dịch đó
            Sử dụng method reference để lấy giá trị amount từ mỗi Transaction
            .reduce(BigDecimal.ZERO, BigDecimal::add):
            Khởi đầu với giá trị 0 (BigDecimal.ZERO)
            Cộng dồn tất cả các số tiền lại với nhau
            Sử dụng method reference BigDecimal::add để thực hiện phép cộng
         */
        BigDecimal dailyTotal = dailyTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (dailyTotal.add(amount).compareTo(DAILY_TRANSFER_LIMIT) > 0) {
            throw new InvalidOperationException("Daily transfer limit exceeded");
        }
    }

    private void publishTransactionEvent(Transaction transaction, String eventType) {
        try {
            TransactionEvent event = new TransactionEvent(transaction);
            kafkaTemplate.send("elevate.transactions", eventType, event);
            //
        } catch (Exception e) {
            log.error("Error publishing transaction event: {}", e.getMessage());
        }
    }


}
