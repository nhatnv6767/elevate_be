package com.elevatebanking.entity.atm;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonTypeName("com.elevatebanking.entity.atm.AtmMachine")
@Table("atm_machines")
@Data
@NoArgsConstructor
public class AtmMachine {
    @PrimaryKey
    private String atmId;

    @Column("bank_code")
    private String bankCode;

    @Column("location")
    private String location;

    @Column("status")
    private String status;

    @Column("denominations")
    private Map<Integer, Integer> denominations;

    @Column("last_updated")
    private LocalDateTime lastUpdated;

    @Column("total_amount")
    private BigDecimal totalAmount;
}
