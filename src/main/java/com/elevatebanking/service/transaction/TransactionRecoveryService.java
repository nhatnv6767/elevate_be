package com.elevatebanking.service.transaction;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.repository.AccountRepository;
import com.elevatebanking.repository.TransactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TransactionRecoveryService {
    TransactionRepository transactionRepository;
    TransactionCompensationService transactionCompensationService;
    AccountRepository accountRepository;
    KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    //    @Value("${spring.kafka.topics.transaction}")
    @NonFinal
    private String transactionTopic = "elevate.transactions";

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void recoverStuckTransactions() {
        log.info("Starting transaction recovery process");
        List<Transaction> stuckTransactions = findStuckTransactions();
        for (Transaction transaction : stuckTransactions) {
            try {
                recoverTransaction(transaction);
            } catch (Exception e) {
                log.error("Failed to recover transaction: {}", transaction.getId(), e);
            }
        }
    }

    List<Transaction> findStuckTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        return transactionRepository.findStuckTransactions(threshold);
    }

    @Transactional
    public void recoverTransaction(Transaction transaction) {
        log.info("Attempting to recover transaction: {}", transaction.getId());
        // check account balances
        boolean balancesConsistent = verifyAccountBalances(transaction);
        if (!balancesConsistent) {
            log.warn("Account balances inconsistent for transaction: {}", transaction.getId());
            transactionCompensationService.compensateTransaction(transaction, "Inconsistent balances during recovery");
            return;
        }

        // try to complete if possible
        if (canCompleteTransaction(transaction)) {
            completeTransaction(transaction);
        } else {
            transactionCompensationService.compensateTransaction(transaction, "Unable to complete during recovery");
        }
    }

    boolean verifyAccountBalances(Transaction transaction) {
        switch (transaction.getType()) {
            case TRANSFER:
                return verifyTransferBalances(transaction);
            case WITHDRAWAL:
                return verifyWithdrawalBalance(transaction);
            case DEPOSIT:
                return verifyDepositBalance(transaction);
            default:
                return false;
        }
    }

    boolean verifyTransferBalances(Transaction transaction) {
        Account fromAccount = transaction.getFromAccount();
        Account toAccount = transaction.getToAccount();
        BigDecimal amount = transaction.getAmount();

        // verify source account wasn't debited or destination wasn't credited
        return !(fromAccount.getBalance().compareTo(amount) < 0 ||
                toAccount.getBalance().add(amount).compareTo(new BigDecimal("999999999999")) > 0); // 999,999,999,999$
    }

    boolean verifyWithdrawalBalance(Transaction transaction) {
        return transaction.getFromAccount().getBalance().compareTo(transaction.getAmount()) >= 0;
    }

    boolean verifyDepositBalance(Transaction transaction) {
        // verify deposit amount doesn't exceed the maximum balance
        // 999,999,999,999$
        // why <=0: because the balance is a positive number
        // why compareTo(new BigDecimal("999999999999")) <= 0: because the balance should not exceed 999,999,999,999
        return transaction.getToAccount().getBalance().add(transaction.getAmount())
                .compareTo(new BigDecimal("999999999999")) <= 0; // 999,999,999,999$
    }

    boolean canCompleteTransaction(Transaction transaction) {
        // check if accounts are still active and have sufficient balance
        return transaction.getFromAccount().getStatus() == AccountStatus.ACTIVE &&
                transaction.getToAccount().getStatus() == AccountStatus.ACTIVE &&
                verifyAccountBalances(transaction);
    }

    @Transactional
    void completeTransaction(Transaction transaction) {
        log.info("Completing recovered transaction: {}", transaction.getId());

        try {
            switch (transaction.getType()) {
                case TRANSFER:
                    completeTransfer(transaction);
                    break;
                case WITHDRAWAL:
                    completeWithdrawal(transaction);
                    break;
                case DEPOSIT:
                    completeDeposit(transaction);
                    break;
                default:
                    log.warn("Unknown transaction type: {}", transaction.getType());
            }

            // update status
            transaction.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(transaction);

            // create and send event
            TransactionEvent event = new TransactionEvent(transaction, "transaction.completed");
            event.addProcessStep("COMPLETED_BY_RECOVERY");
            kafkaTemplate.send(transactionTopic, event.getTransactionId(), event);
            log.info("Successfully completed recovered transaction: {}", transaction.getId());
        } catch (Exception e) {
            log.error("Failed to complete recovered transaction: {}", transaction.getId(), e);
            transactionCompensationService.compensateTransaction(transaction, "Failed during recovery completion: " + e.getMessage());
            throw e;
        }
    }

    void completeTransfer(Transaction transaction) {
        Account fromAccount = transaction.getFromAccount();
        Account toAccount = transaction.getToAccount();
        BigDecimal amount = transaction.getAmount();

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InvalidOperationException("Insufficient balance during recovery");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }

    void completeWithdrawal(Transaction transaction) {
        Account account = transaction.getFromAccount();
        BigDecimal amount = transaction.getAmount();

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InvalidOperationException("Insufficient balance during recovery");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }

    void completeDeposit(Transaction transaction) {
        Account account = transaction.getToAccount();
        BigDecimal amount = transaction.getAmount();

        if (account.getBalance().add(amount).compareTo(new BigDecimal("999999999999")) > 0) {
            throw new InvalidOperationException("Deposit amount exceeds maximum balance during recovery");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }


}
