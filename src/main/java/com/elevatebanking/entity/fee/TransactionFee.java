package com.elevatebanking.entity.fee;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("transaction_fees")
@Data
@NoArgsConstructor
public class TransactionFee {

    @PrimaryKeyColumn(name = "fee_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID feeId;

    @PrimaryKeyColumn(name = "transaction_time", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private LocalDateTime transactionTime;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("source_bank")
    private String sourceBank;

    @Column("target_bank")
    private String targetBank;

    @Column("fee_amount")
    private BigDecimal feeAmount;

    @Column("fee_type")
    private String feeType; // WITHDRAWAL_FEE, TRANSFER_FEE

    @Column("status")
    private String status;

}
