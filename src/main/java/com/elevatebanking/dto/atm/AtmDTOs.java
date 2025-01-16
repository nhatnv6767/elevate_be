package com.elevatebanking.dto.atm;

import com.elevatebanking.entity.atm.AtmMachine;
import lombok.Builder;
import lombok.Data;

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
}
