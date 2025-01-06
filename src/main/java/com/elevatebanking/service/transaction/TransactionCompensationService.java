package com.elevatebanking.service.transaction;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.repository.AccountRepository;
import com.elevatebanking.repository.TransactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TransactionCompensationService {
    TransactionRepository transactionRepository;
    AccountRepository accountRepository;
    KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    //    @Value("${spring.kafka.topics.transaction}")
    @NonFinal
    private String transactionTopic = "elevate.transactions";

    @Transactional
    public void compensateTransaction(Transaction transaction, String reason) {
        log.info("Starting compensation for transaction: {}, reason: {}", transaction.getId(), reason);

        try {
            switch (transaction.getType()) {
                case TRANSFER -> compensateTransfer(transaction);
                case WITHDRAWAL -> compensateWithdrawal(transaction);
                case DEPOSIT -> compensateDeposit(transaction);
                default -> log.warn("Unknown transaction type: {}", transaction.getType());
            }
            // update transaction status
            transaction.setStatus(TransactionStatus.ROLLED_BACK);
            transactionRepository.save(transaction);

            // publish compensation event
            publishCompensationEvent(transaction, reason);
        } catch (Exception e) {
            log.error("Failed to compensate transaction: {}", transaction.getId(), e);
            transaction.setStatus(TransactionStatus.ROLLBACK_FAILED);
            transactionRepository.save(transaction);
            throw e;
        }

    }

    void compensateTransfer(Transaction transaction) {
        Account fromAccount = transaction.getFromAccount();
        Account toAccount = transaction.getToAccount();

        if (fromAccount != null) {
            fromAccount.setBalance(fromAccount.getBalance().add(transaction.getAmount()));
            accountRepository.save(fromAccount);
        }
        if (toAccount != null) {
            toAccount.setBalance(toAccount.getBalance().subtract(transaction.getAmount()));
            accountRepository.save(toAccount);
        }
    }

    void compensateWithdrawal(Transaction transaction) {
        Account account = transaction.getFromAccount();
        if (account != null) {
            account.setBalance(account.getBalance().add(transaction.getAmount()));
            accountRepository.save(account);
        }
    }

    void compensateDeposit(Transaction transaction) {
        Account account = transaction.getToAccount();
        if (account != null) {
            account.setBalance(account.getBalance().subtract(transaction.getAmount()));
            accountRepository.save(account);
        }
    }

    void publishCompensationEvent(Transaction transaction, String reason) {
        TransactionEvent event = new TransactionEvent(transaction, "transaction.compensated");
        event.addProcessStep("COMPENSATED: " + reason);
        kafkaTemplate.send(String.valueOf(transactionTopic), event.getTransactionId(), event);
    }
}
