package com.elevatebanking.repository;

import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByFromAccountId(String accountId);

    List<Transaction> findByToAccountId(String accountId);

    @Query("SELECT t FROM Transaction t WHERE t.fromAccount.id = :accountId OR t.toAccount.id = :accountId")
    List<Transaction> findTransactionsByAccountId(@Param("accountId") String accountId);

    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.createdAt < :timeout")
    List<Transaction> findTransactionsByStatusAndOlderThan(
            @Param("status") TransactionStatus status,
            @Param("timeout") LocalDateTime timeout
    );

    @Query("SELECT t FROM Transaction t WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId) AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsByAccountAndDateRange(
            @Param("accountId") String accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // findPendingTransactionsOlderThan(timeout)
    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING' AND t.createdAt < :timeout")
    List<Transaction> findPendingTransactionsOlderThan(@Param("timeout") LocalDateTime timeout);

    @Query("SELECT count (t) from Transaction t where t.fromAccount.id =:accountId and t.createdAt >=:since")
    long countTransactionsInTimeframe(@Param("accountId") String accountId, @Param("since") LocalDateTime since);

    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING' AND t.createdAt < :threshold")
    List<Transaction> findStuckTransactions(LocalDateTime threshold);

    @Query("SELECT count(t) FROM Transaction t WHERE t.createdAt BETWEEN :start AND :end")
    long countTransactionsInTimeframe(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT count(t) FROM Transaction t WHERE t.status = :status AND t.createdAt BETWEEN :start AND :end")
    long countTransactionsByStatusInTimeframe(
            @Param("status") TransactionStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(AVG((CAST(t.updatedAt as long) - CAST(t.createdAt as long)) / 1000000.0), 0) " +
            "FROM Transaction t " +
            "WHERE t.createdAt BETWEEN :start AND :end " +
            "AND t.status = 'COMPLETED'")
    double calculateAverageProcessingTime(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // that means: find all transactions with status and createdAtAfter
    List<Transaction> findByStatusAndCreatedAtAfter(
            TransactionStatus status,
            LocalDateTime since);

    @Query("SELECT t FROM Transaction t WHERE (t.fromAccount.user.id = :userId or t.toAccount.user.id = :userId) AND t.createdAt BETWEEN :startDate AND :endDate")
    List<Transaction> findTransactionsByUserAndDateRange(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT count(t) FROM Transaction t WHERE (t.fromAccount.user.id = :userId or t.toAccount.user.id = :userId) AND t.createdAt BETWEEN :startDate AND :endDate and t.status = 'COMPLETED'")
    Long countTransactionsByUserInTimeRange(String userId, LocalDateTime startTime, LocalDateTime now);
}
