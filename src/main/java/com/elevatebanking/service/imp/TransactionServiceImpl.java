package com.elevatebanking.service.imp;

import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.TransactionEvent;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class TransactionServiceImpl implements ITransactionService {

    private final TransactionRepository transactionRepository;
    private final IAccountService accountService;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal(5000000); // 5,000,000$
    private static final BigDecimal SINGLE_TRANSFER_LIMIT = new BigDecimal(1000000); // 1,000,000$

    @Autowired
    public TransactionServiceImpl(TransactionRepository transactionRepository, IAccountService accountService, KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.kafkaTemplate = kafkaTemplate;
    }


    @Override
    public Transaction createTransaction(Transaction transaction) {
        return null;
    }

    @Override
    public Transaction processTransfer(String fromAccountId, String toAccountId, BigDecimal amount, String description) {
        // Validate accounts and balance
        accountService.validateAccount(fromAccountId, amount);
        accountService.validateAccount(toAccountId, null);

        Transaction transaction = new Transaction();
        transaction.setFromAccount(accountService.getAccountById(fromAccountId).get());
        transaction.setToAccount(accountService.getAccountById(toAccountId).get());
        transaction.setAmount(amount);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setDescription(description);

        // Execute transfer
        BigDecimal fromBalance = accountService.getBalance(fromAccountId).subtract(amount);
        BigDecimal toBalance = accountService.getBalance(toAccountId).add(amount);

        accountService.updateBalance(fromAccountId, fromBalance);
        accountService.updateBalance(toAccountId, toBalance);

        transaction.setStatus(TransactionStatus.COMPLETED);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // publish event
        kafkaTemplate.send("elevate.transactions", new TransactionEvent(savedTransaction));

        return savedTransaction;
    }

    @Override
    public Transaction processDeposit(String accountId, BigDecimal amount) {
        accountService.validateAccount(accountId, null);

        Transaction transaction = new Transaction();
        transaction.setToAccount(accountService.getAccountById(accountId).get());
        transaction.setAmount(amount);
        transaction.setType(TransactionType.DEPOSIT);

        BigDecimal newBalance = accountService.getBalance(accountId).add(amount);
        accountService.updateBalance(accountId, newBalance);

        transaction.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
    }

    @Override
    public Transaction processWithdrawal(String accountId, BigDecimal amount) {
        accountService.validateAccount(accountId, amount);

        Transaction transaction = new Transaction();
        transaction.setFromAccount(accountService.getAccountById(accountId).get());
        transaction.setAmount(amount);
        transaction.setType(TransactionType.WITHDRAWAL);

        BigDecimal newBalance = accountService.getBalance(accountId).subtract(amount);
        accountService.updateBalance(accountId, newBalance);

        transaction.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
    }

    @Override
    public Optional<Transaction> getTransactionById(String id) {
        return Optional.empty();
    }

    @Override
    public List<Transaction> getTransactionsByAccountId(String accountId) {
        return List.of();
    }

    @Override
    public List<Transaction> getPendingTransactions() {
        return List.of();
    }

    @Override
    public void cancelTransaction(String transactionId) {

    }


}
