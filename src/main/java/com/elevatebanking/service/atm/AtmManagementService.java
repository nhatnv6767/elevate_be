package com.elevatebanking.service.atm;

import com.elevatebanking.dto.atm.AtmDTOs.*;
import com.elevatebanking.entity.atm.AtmMachine;
import com.elevatebanking.entity.fee.TransactionFee;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.cassandra.AtmRepository;
import com.elevatebanking.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class AtmManagementService {
    private final AtmRepository atmRepository;
    private final SecurityUtils securityUtils;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public AtmMachine createAtm(AtmCreationRequest request) {
        validateDenominations(request.getInitialDenominations());

        AtmMachine atm = new AtmMachine();
        atm.setAtmId(UUID.randomUUID().toString());
        atm.setBankCode(request.getBankCode());
        atm.setLocation(request.getLocation());
        atm.setStatus("ACTIVE");
        atm.setDenominations(request.getInitialDenominations());
        atm.setTotalAmount(calculateTotal(request.getInitialDenominations()));
        atm.setLastUpdated(LocalDateTime.now());
        return atmRepository.save(atm);
    }

    @Transactional
    public AtmMachine updateAtmDenominations(String atmId, Map<Integer, Integer> newDenominations) {
        AtmMachine atm = getAtmById(atmId);
        // validate denominations
        validateDenominations(newDenominations);

        // calculate new total
        BigDecimal newTotal = calculateTotal(newDenominations);

        // update atm
        atm.setDenominations(newDenominations);
        atm.setTotalAmount(newTotal);
        atm.setLastUpdated(LocalDateTime.now());

        // update cache redis, tracking money in atm
        updateAtmCache(atm);
        return atmRepository.save(atm);
    }

    @Transactional(readOnly = true)
    public boolean canProcessWithdrawal(String atmId, BigDecimal amount, Map<Integer, Integer> requestedDenominations) {
        AtmMachine atm = getAtmById(atmId);

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

    @Transactional(readOnly = true)
    public List<AtmMachine> getAllAtms() {
        return atmRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AtmMachine getAtmById(String atmId) {
        return atmRepository.findById(atmId).orElseThrow(() -> new ResourceNotFoundException("Atm not found"));
    }

    @Transactional
    public AtmMachine updateAtmStatus(String atmId, String status) {
        AtmMachine atm = getAtmById(atmId);

        if (!isValidStatus(status)) {
            throw new InvalidOperationException("Invalid ATM status: " + status);
        }

        atm.setStatus(status);
        atm.setLastUpdated(LocalDateTime.now());
        return atmRepository.save(atm);
    }

    private void validateDenominations(Map<Integer, Integer> denominations) {
        Set<Integer> validDenoms = Set.of(100, 50, 20, 10, 5, 1);
        for (Map.Entry<Integer, Integer> entry : denominations.entrySet()) {
            if (!validDenoms.contains(entry.getKey())) {
                throw new InvalidOperationException("Invalid denomination: " + entry.getKey());
            }
            if (entry.getValue() < 0) {
                throw new InvalidOperationException("Invalid denomination count: " + entry.getValue());
            }
        }
    }

    private BigDecimal calculateTotal(Map<Integer, Integer> denominations) {
        return denominations.entrySet().stream()
                .map(entry -> new BigDecimal(entry.getKey() * entry.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isValidStatus(String status) {
        return Arrays.asList("ACTIVE", "INACTIVE", "MAINTENANCE", "OUT_OF_SERVICE").contains(status);
    }

    private void updateAtmCache(AtmMachine atm) {
        try {
            String cacheKey = "atm:balance:" + atm.getAtmId();
            redisTemplate.opsForValue().set(cacheKey, atm.getTotalAmount().toString());
            redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Error updating ATM cache: {}", e.getMessage());
        }
    }

    public Map<Integer, Integer> subtractDenominations(
            Map<Integer, Integer> atmDenoms,
            Map<Integer, Integer> requestedDenoms
    ) {
        Map<Integer, Integer> result = new HashMap<>(atmDenoms);
        for (Map.Entry<Integer, Integer> entry : requestedDenoms.entrySet()) {
            Integer denom = entry.getKey();
            Integer requested = entry.getValue();
            Integer available = result.getOrDefault(denom, 0);
            if (available < requested) {
                throw new InvalidOperationException(
                        String.format("Not enough %d bills available in ATM", denom)
                );
            }
            result.put(denom, available - requested);
        }
        return result;
    }
}
