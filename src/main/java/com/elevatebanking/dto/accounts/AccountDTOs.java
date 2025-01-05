package com.elevatebanking.dto.accounts;

import com.elevatebanking.entity.enums.AccountStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


public class AccountDTOs {

    @Data
    @Builder
    public static class CreateAccountRequest {
        @NotBlank(message = "User ID is required")
        private String userId;

        @DecimalMin(value = "0.0", message = "Initial balance cannot be negative")
        @Digits(integer = 18, fraction = 2, message = "Invalid balance format")
        private BigDecimal initialBalance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountResponse {
        private String id;
        private String userId;
        private String accountNumber;
        private BigDecimal balance;
        private AccountStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalanceResponse {
        private String accountNumber;
        private BigDecimal balance;
        private LocalDateTime lastUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountSummaryResponse {
        private String id;
        private String accountNumber;
        private AccountStatus status;
        private BigDecimal balance;
    }

}
