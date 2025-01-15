package com.elevatebanking.repository.cassandra;

import com.elevatebanking.entity.fee.TransactionFee;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionFeeRepository extends CassandraRepository<TransactionFee, UUID> {
    List<TransactionFee> findBySourceBankAndTransactionTimeBetween(String sourceBank, LocalDateTime startTime, LocalDateTime endTime);

    List<TransactionFee> findByFeeTypeAndTransactionTimeBetween(String feeType, LocalDateTime startTime, LocalDateTime endTime);

    @Query(allowFiltering = true)
    List<TransactionFee> findByTransactionId(UUID transactionId);
}
