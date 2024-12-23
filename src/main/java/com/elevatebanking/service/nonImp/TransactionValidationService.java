package com.elevatebanking.service.nonImp;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionValidationService {
    private final TransactionRepository transactionRepository;
    private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal(5000000); // 5,000,000$
    private static final BigDecimal SINGLE_TRANSFER_LIMIT = new BigDecimal(1000000); // 1,000,000$

    public void validateTransferTransaction(Account fromAccount, Account toAccount, BigDecimal amount) {
        validateAccountStatus(fromAccount);
        validateAccountStatus(toAccount);
        validateTransactionAmount(amount);
        validateDailyLimit(fromAccount.getId(), amount);
        validateSufficientBalance(fromAccount, amount);
        validateSameAccount(fromAccount, toAccount);
    }

    public void validateDepositTransaction(Account account, BigDecimal amount) {
        validateAccountStatus(account);
        validateTransactionAmount(amount);
        validateDailyLimit(account.getId(), amount);
    }

    public void validateWithdrawalTransaction(Account account, BigDecimal amount) {
        validateAccountStatus(account);
        validateTransactionAmount(amount);
        validateDailyLimit(account.getId(), amount);
        validateSufficientBalance(account, amount);
    }

    private void validateAccountStatus(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidOperationException(
                    String.format("Account %s is not active", account.getAccountNumber())
            );
        }
    }

    private void validateTransactionAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("Amount must be greater than 0");
        }

        if (amount.compareTo(SINGLE_TRANSFER_LIMIT) > 0) {
            throw new InvalidOperationException(
                    String.format("Transaction amount exceeds limit of %s", SINGLE_TRANSFER_LIMIT)
            );
        }
    }

    private void validateDailyLimit(String accountId, BigDecimal amount) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        List<Transaction> dailyTransactions = transactionRepository.findTransactionsByAccountAndDateRange(accountId, startOfDay, LocalDateTime.now());

        BigDecimal dailyTotal = dailyTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (dailyTotal.add(amount).compareTo(DAILY_TRANSFER_LIMIT) > 0) {
            throw new InvalidOperationException(
                    String.format("Daily transfer limit exceeded. Current limit: %s", DAILY_TRANSFER_LIMIT)
            );
        }
    }

    private void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InvalidOperationException("Insufficient balance");
        }
    }

    private void validateSameAccount(Account fromAccount, Account toAccount) {
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new InvalidOperationException("Cannot transfer to the same account");
        }
    }
}
