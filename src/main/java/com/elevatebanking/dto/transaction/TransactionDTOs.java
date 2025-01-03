package com.elevatebanking.dto.transaction;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TransactionDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionRequest {
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @DecimalMax(value = "1000000.00", message = "Amount must be less than 1,000,000")
        private BigDecimal amount;

        @Size(max = 500, message = "Description must be less than 500 characters")
        private String description;
    }

    @Getter
    @Setter
//    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferRequest extends TransactionRequest {

        @NotBlank(message = "From account id is required")
        private String fromAccountId;

        @NotBlank(message = "To account id is required")
        private String toAccountId;

        @NotNull(message = "Transfer type is required")
        private TransferType type;

        public enum TransferType {
            INTERNAL, EXTERNAL
        }
    }

    @Data
//    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepositRequest extends TransactionRequest {
        @NotBlank(message = "Account id is required")
        private String accountId;
    }


    @Data
//    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawRequest extends TransactionRequest {
        @NotBlank(message = "Account id is required")
        private String accountId;
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

    @Data
//    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduledTransactionRequest extends TransactionRequest {
        @NotBlank(message = "From account id is required")
        private String fromAccountId;

        @NotBlank(message = "To account id is required")
        private String toAccountId;

        @NotNull(message = "Scheduled time is required")
        @Future(message = "Scheduled time must be in the future")
        private LocalDateTime scheduledTime;

        @NotNull(message = "Scheduled type is required")
        private ScheduledType type;

        private Integer repeatCount; // number of times to repeat the transaction, null if one-time transaction
        private Integer intervalDays; // interval between repeated transactions in days, null if one-time transaction

        public enum ScheduledType {
            ONE_TIME, DAILY, WEEKLY, MONTHLY
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ScheduledTransactionResponse {
            private String scheduledId;
            private String status;
            private LocalDateTime nextExecutionTime;
            private LocalDateTime createdAt;
            private int remainingExecutions;
            private TransactionRequest originalRequest;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ScheduleModificationRequest {
            @NotNull(message = "Scheduled id is required")
            private String scheduledId;

            private LocalDateTime newScheduledTime;
            private Integer newRepeatCount;
            private Integer newIntervalDays;

            @Size(max = 500, message = "Reason must be less than 500 characters")
            private String modificationReason;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ScheduledTransactionListResponse {
            private List<ScheduledTransactionResponse> scheduledTransactions;
            private int totalCount;
            private boolean hasMore;
            private String nextPageToken;
        }
    }


}
