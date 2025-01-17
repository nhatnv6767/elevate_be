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
import java.math.RoundingMode;
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
        if (atmId == null || atmId.trim().isEmpty()) {
            throw new InvalidOperationException("ATM ID cannot be null or empty");
        }
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
        if (atmId == null || atmId.trim().isEmpty()) {
            throw new InvalidOperationException("ATM ID cannot be null or empty");
        }
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
            Map<Integer, Integer> requestedDenoms) {
        Map<Integer, Integer> result = new HashMap<>(atmDenoms);
        for (Map.Entry<Integer, Integer> entry : requestedDenoms.entrySet()) {
            Integer denom = entry.getKey();
            Integer requested = entry.getValue();
            Integer available = result.getOrDefault(denom, 0);
            if (available < requested) {
                throw new InvalidOperationException(
                        String.format("Not enough %d bills available in ATM", denom));
            }
            result.put(denom, available - requested);
        }
        return result;
    }

    @Transactional
    public AtmMachine addAtmDenominations(String atmId, Map<Integer, Integer> denominationsToAdd) {
        AtmMachine atm = getAtmById(atmId);

        // validate new denominations
        validateDenominations(denominationsToAdd);
        // merge existing and new denominations
        Map<Integer, Integer> updatedDenominations = new HashMap<>(atm.getDenominations());
        for (Map.Entry<Integer, Integer> entry : denominationsToAdd.entrySet()) {
            Integer denom = entry.getKey();
            Integer additionalCount = entry.getValue();

            // add new amount to existing amount (or 0 if denomination didn't exist)
            updatedDenominations.merge(denom, additionalCount, Integer::sum);
        }
        // calculate new total
        BigDecimal newTotal = calculateTotal(updatedDenominations);
        // update atm
        atm.setDenominations(updatedDenominations);
        atm.setTotalAmount(newTotal);
        atm.setLastUpdated(LocalDateTime.now());

        updateAtmCache(atm);
        return atmRepository.save(atm);
    }

    /**
     * Tính toán số lượng tờ tiền tối ưu cho một giao dịch rút tiền ATM.
     * 
     * Chi tiết nghiệp vụ:
     * 1. Đầu vào:
     * - atmId: ID của máy ATM
     * - amount: Số tiền cần rút (dạng BigDecimal)
     * 
     * 2. Quy trình xử lý:
     * - Lấy thông tin máy ATM và danh sách mệnh giá tiền hiện có
     * - Chuyển đổi số tiền cần rút sang đơn vị cent (nhân 100) và làm tròn
     * - Sắp xếp các mệnh giá theo thứ tự giảm dần
     * - Với mỗi mệnh giá:
     * + Kiểm tra số lượng tờ tiền có sẵn trong ATM
     * + Tính số lượng tờ tiền cần thiết bằng cách chia số tiền còn lại cho mệnh giá
     * + Chọn số lượng tờ tiền thực tế = min(số lượng cần, số lượng có sẵn)
     * + Cập nhật số tiền còn lại cần rút
     * - Nếu còn tiền chưa rút được -> báo lỗi không đủ tiền
     * 
     * 3. Kết quả trả về:
     * - Map<Integer, Integer> chứa cặp <mệnh giá, số lượng tờ tiền>
     * - Ví dụ: {100: 5, 50: 1, 20: 2} nghĩa là 5 tờ 100$, 1 tờ 50$, 2 tờ 20$
     */
    public Map<Integer, Integer> calculateOptimalDenominations(String atmId, BigDecimal amount) {
        AtmMachine atm = getAtmById(atmId);
        Map<Integer, Integer> availableDenominations = atm.getDenominations();
        Map<Integer, Integer> result = new HashMap<>();

        // Chuyển đổi số tiền sang cent và làm tròn đến 0 chữ số thập phân
        int remainingAmount = amount
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        List<Integer> denominations = new ArrayList<>(availableDenominations.keySet());
        denominations.sort(Collections.reverseOrder());

        for (Integer denom : denominations) {
            if (remainingAmount <= 0)
                break;

            int availableCount = availableDenominations.get(denom);

            // Sửa lại công thức tính số lượng tờ tiền cần thiết
            // Vì remainingAmount đã là cent, chỉ cần chia cho denom
            int neededCount = remainingAmount / denom;

            int actualCount = Math.min(neededCount, availableCount);

            if (actualCount > 0) {
                result.put(denom, actualCount);
                // Cập nhật số tiền còn lại (vẫn tính bằng cent)
                remainingAmount -= actualCount * denom;
            }
        }

        // Nếu còn tiền chưa rút được -> không đủ tiền trong ATM
        if (remainingAmount > 0) {
            throw new InvalidOperationException("ATM doesn't have enough bills to process this withdrawal");
        }

        return result;
    }
}
