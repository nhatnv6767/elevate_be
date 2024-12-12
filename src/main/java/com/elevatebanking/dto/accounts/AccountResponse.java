package com.elevatebanking.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private String id;
    private Integer version;
    private String accountNumber;
    private Double balance;
    private String status;
    private String userId;

}
