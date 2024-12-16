package com.elevatebanking.service;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.transaction.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ITransactionService {

    Transaction createTransaction(Transaction transaction);

    Transaction processTransfer(String fromAccountId, String toAccountId, BigDecimal amount, String description);

    Transaction processDeposit(String accountId, BigDecimal amount);

    Transaction processWithdrawal(String accountId, BigDecimal amount);

    Optional<Transaction> getTransactionById(String id);

    List<Transaction> getTransactionsByAccountId(String accountId);

    List<Transaction> getPendingTransactions();

    void cancelTransaction(String transactionId);


    // new service

    TransactionResponse transfer(TransferRequest request);

    TransactionResponse deposit(DepositRequest request);

    TransactionResponse withdraw(WithdrawRequest request);

    TransactionResponse getTransaction(String id);

    List<TransactionHistoryResponse> getTransactionHistory(String accountId, LocalDateTime startDate, LocalDateTime endDate);
}
