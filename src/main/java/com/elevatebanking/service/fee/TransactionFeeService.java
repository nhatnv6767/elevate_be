package com.elevatebanking.service.fee;

import com.elevatebanking.entity.fee.TransactionFee;
import com.elevatebanking.repository.cassandra.TransactionFeeRepository;
import com.elevatebanking.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionFeeService {
    private final TransactionFeeRepository feeRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public TransactionFee recordTransactionFee(String sourceBank, String targetBank, BigDecimal amount, String feeType, UUID transactionId) {
        TransactionFee fee = new TransactionFee();
        fee.setFeeId(UUID.randomUUID());
        fee.setTransactionId(transactionId);
        fee.setSourceBank(sourceBank);
        fee.setTargetBank(targetBank);
        fee.setFeeAmount(amount);
        fee.setFeeType(feeType);
        fee.setTransactionTime(LocalDateTime.now());
        fee.setStatus("COMPLETED");
        //
        return feeRepository.save(fee);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateTotalFees(String bankCode, LocalDateTime startTime, LocalDateTime endTime) {
        List<TransactionFee> fees = feeRepository.findBySourceBankAndTransactionTimeBetween(bankCode, startTime, endTime);

        return fees.stream().map(TransactionFee::getFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<TransactionFee> getFeeHistory(String bankCode, LocalDateTime startTime, LocalDateTime endTime) {
        return feeRepository.findBySourceBankAndTransactionTimeBetween(bankCode, startTime, endTime);
    }

}
