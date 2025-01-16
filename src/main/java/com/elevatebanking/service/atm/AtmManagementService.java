package com.elevatebanking.service.atm;

import com.elevatebanking.entity.atm.AtmMachine;
import com.elevatebanking.entity.fee.TransactionFee;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.cassandra.AtmRepository;
import com.elevatebanking.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AtmManagementService {
    private final AtmRepository atmRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public AtmMachine updateAtmDenominations(String atmId, Map<Integer, Integer> newDenominations) {
        AtmMachine atm = atmRepository.findById(atmId).orElseThrow(() -> new ResourceNotFoundException("Atm not found"));
        // validate denominations
        validateDenominations(newDenominations);

        // calculate new total
        BigDecimal newTotal = calculateTotal(newDenominations);

        // update atm
        atm.setDenominations(newDenominations);
        atm.setTotalAmount(newTotal);
        atm.setLastUpdated(LocalDateTime.now());

        return atmRepository.save(atm);
    }

    @Transactional(readOnly = true)
    public boolean canProcessWithdrawal(String atmId, BigDecimal amount, Map<Integer, Integer> requestedDenominations) {
        AtmMachine atm = atmRepository.findById(atmId).orElseThrow(() -> new ResourceNotFoundException("Atm not found"));

        // check if ATM has enough money
        if (atm.getTotalAmount().compareTo(amount) < 0) {
            return false;
        }

        // check if ATM has enough of each denomination
        Map<Integer, Integer> atmDenoms = atm.getDenominations();
        for (Map.Entry<Integer, Integer> entry : requestedDenominations.entrySet()) {
            Integer denom = entry.getKey();
            Integer requested = entry.getValue();
            Integer available = atmDenoms.getOrDefault(denom, 0);
            if (available < requested) {
                return false;
            }
        }
        return true;
    }

    private void validateDenominations(Map<Integer, Integer> denominations) {
        Set<Integer> validDenoms = Set.of(100, 50, 20, 10, 5, 1);
        for (Integer denom : denominations.keySet()) {
            if (!validDenoms.contains(denom)) {
                throw new InvalidOperationException("Invalid denomination: " + denom);
            }
        }
    }

    private BigDecimal calculateTotal(Map<Integer, Integer> denominations) {
        return denominations.entrySet().stream()
                .map(entry -> new BigDecimal(entry.getKey() * entry.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
