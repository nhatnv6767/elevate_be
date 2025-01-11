package com.elevatebanking.service;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.transaction.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ITransactionService {

    Transaction createTransaction(Transaction transaction) throws InterruptedException;

    Transaction processTransfer(String fromAccountId, String toAccountId, BigDecimal amount, String description);

    Transaction processDeposit(String accountId, BigDecimal amount) throws InterruptedException;

    Transaction processWithdrawal(String accountId, BigDecimal amount) throws InterruptedException;

    Optional<Transaction> getTransactionById(String id);

    List<Transaction> getTransactionsByAccountId(String accountId);

    List<Transaction> getPendingTransactions();

    void cancelTransaction(String transactionId);


    // new service

    TransactionResponse transfer(TransferRequest request) throws InterruptedException;

    TransactionResponse deposit(DepositRequest request) throws InterruptedException;

    TransactionResponse withdraw(WithdrawRequest request) throws InterruptedException;

    TransactionResponse getTransaction(String id);

    Page<TransactionHistoryResponse> getTransactionHistory(String accountId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}
