package com.elevatebanking.dto.atm;

import com.elevatebanking.dto.transaction.TransactionDTOs;
import com.elevatebanking.entity.atm.AtmMachine;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public class AtmDTOs {
    @Data
    @Builder
    public static class AtmCreationRequest {
        private String bankCode;
        private String location;
        private Map<Integer, Integer> initialDenominations;
    }

    @Data
    @Builder
    public static class AtmResponse {
        private String atmId;
        private String bankCode;
        private String location;
        private String status;
        private Map<Integer, Integer> denominations;
        private BigDecimal totalAmount;
        private LocalDateTime lastUpdated;

        public static AtmResponse from(AtmMachine atm) {
            return AtmResponse.builder()
                    .atmId(atm.getAtmId())
                    .bankCode(atm.getBankCode())
                    .location(atm.getLocation())
                    .status(atm.getStatus())
                    .denominations(atm.getDenominations())
                    .totalAmount(atm.getTotalAmount())
                    .lastUpdated(atm.getLastUpdated())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeDepositRequest extends TransactionDTOs.DepositRequest {
        @NotBlank(message = "Payment method id is required")
        private String paymentMethodId;

        private String currency = "USD";
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeDepositResponse extends TransactionDTOs.TransactionResponse {
        private String paymentIntentId;
        private String paymentStatus;
        private Map<String, Object> paymentMetadata;

        // Constructor để hỗ trợ building
        public StripeDepositResponse(
                String transactionId,
                String paymentIntentId,
                BigDecimal amount,
                String status,
                LocalDateTime timestamp
        ) {
            super(transactionId, "STRIPE_DEPOSIT", amount, status, null, null, null, timestamp);
            this.paymentIntentId = paymentIntentId;
        }

        // Thêm builder method tĩnh
        public static StripeDepositResponseBuilder stripeBuilder() {
            return new StripeDepositResponseBuilder();
        }

        public static class StripeDepositResponseBuilder {
            private StripeDepositResponse response = new StripeDepositResponse();

            public StripeDepositResponseBuilder transactionId(String transactionId) {
                response.setTransactionId(transactionId);
                return this;
            }

            public StripeDepositResponseBuilder paymentIntentId(String paymentIntentId) {
                response.setPaymentIntentId(paymentIntentId);
                return this;
            }

            public StripeDepositResponseBuilder amount(BigDecimal amount) {
                response.setAmount(amount);
                return this;
            }

            public StripeDepositResponseBuilder status(String status) {
                response.setStatus(status);
                return this;
            }

            public StripeDepositResponseBuilder timestamp(LocalDateTime timestamp) {
                response.setTimestamp(timestamp);
                return this;
            }

            public StripeDepositResponse build() {
                return response;
            }
        }
    }
}
