package com.elevatebanking.dto.transaction;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferRequest {

        @NotBlank(message = "From account id is required")
        private String fromAccountId;

        @NotBlank(message = "To account id is required")
        private String toAccountId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "1000000.00", message = "Amount must be less than 1,000,000")
        private BigDecimal amount;

        @Size(max = 500, message = "Description must be less than 500 characters")
        private String description;

        @NotNull(message = "Transfer type is required")
        private TransferType type;

        public enum TransferType {
            INTERNAL, EXTERNAL
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepositRequest {
        @NotBlank(message = "Account id is required")
        private String accountId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "1000000.00", message = "Amount must be less than 1,000,000")
        private BigDecimal amount;

        @Size(max = 500, message = "Description must be less than 500 characters")
        private String description;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawRequest {
        @NotBlank(message = "Account id is required")
        private String accountId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "1000000.00", message = "Amount must be less than 1,000,000")
        private BigDecimal amount;

        @Size(max = 500, message = "Description must be less than 500 characters")
        private String description;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private String transactionId;
        private String type;
        private BigDecimal amount;
        private String status;
        private String fromAccount;
        private String toAccount;
        private String description;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionHistoryResponse {
        private String transactionId;
        private String type;
        private BigDecimal amount;
        private String status;
        private String description;
        private LocalDateTime timestamp;
        private TransactionParty from;
        private TransactionParty to;


        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TransactionParty {
            private String accountId;
            private String accountNumber;
            private String accountName;
        }

    }


}
