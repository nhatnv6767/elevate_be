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
     * Tính toán số tờ tiền tối ưu cho một giao dịch rút tiền ATM
     * <p>
     * Thuật toán chính:
     * 1. Lấy thông tin ATM và kiểm tra đầu vào
     * 2. Sắp xếp mệnh giá theo thứ tự giảm dần để ưu tiên tờ tiền lớn
     * 3. Với mỗi mệnh giá:
     * - Tính số tờ tiền cần thiết
     * - Kiểm tra và điều chỉnh dựa trên số tờ có sẵn
     * - Tối ưu hóa bằng cách xem xét các mệnh giá nhỏ hơn
     * 4. Kiểm tra kết quả cuối cùng
     */
    @Transactional
    public Map<Integer, Integer> calculateOptimalDenominations(String atmId, BigDecimal amount) {
        // Bước 1: Lấy thông tin ATM và khởi tạo
        AtmMachine atm = getAtmById(atmId);
        Map<Integer, Integer> availableDenominations = atm.getDenominations();
        Map<Integer, Integer> result = new HashMap<>();

        // Kiểm tra số tiền phải là số nguyên
        if (amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidOperationException("Amount must be greater than 0");
        }

        int targetAmount = amount.intValue();
        int remainingAmount = targetAmount;

        // Bước 2: Sắp xếp mệnh giá giảm dần (ví dụ: $100 -> $50 -> $20 -> $10)
        List<Integer> denominations = new ArrayList<>(availableDenominations.keySet());
        denominations.sort(Collections.reverseOrder());

        // Bước 3: Xử lý từng mệnh giá
        for (int i = 0; i < denominations.size(); i++) {
            Integer denom = denominations.get(i);
            if (remainingAmount <= 0) // Đã đủ tiền
                break;

            int availableCount = availableDenominations.getOrDefault(denom, 0);
            if (availableCount <= 0) // Bỏ qua mệnh giá không có sẵn
                continue;

            // Tính số tờ tiền cần thiết
            // Ví dụ: Cần $300, mệnh giá $50 -> cần 6 tờ
            int neededCount = remainingAmount / denom;
            // Lấy số nhỏ nhất giữa số tờ cần và số tờ có sẵn trong ATM
            int actualCount = Math.min(neededCount, availableCount);

            // Nếu số tờ tiền thực tế cần dùng > 0
            if (actualCount > 0) {
                // Thêm mệnh giá và số lượng tờ tiền vào kết quả
                // Ví dụ: {50 => 3} nghĩa là cần 3 tờ mệnh giá 50
                result.put(denom, actualCount);

                // Trừ đi số tiền đã được xử lý
                // Ví dụ: Cần rút 300, dùng 3 tờ 50 => Còn lại 150
                // actualCount (3) * denom (50) = 150
                remainingAmount -= actualCount * denom;
            }

            // Tối ưu cho mệnh giá lớn
            // Xem xét dùng thêm một tờ mệnh giá lớn nếu mệnh giá nhỏ không đủ
            if (remainingAmount > 0 && i < denominations.size() - 1) {
                // Tính số tiền có thể rút với mệnh giá nhỏ hơn
                int possibleWithSmaller = calculatePossibleAmount(
                        denominations.subList(i + 1, denominations.size()),
                        availableDenominations,
                        remainingAmount);

                // Nếu mệnh giá nhỏ không đủ và còn tờ mệnh giá lớn
                if (possibleWithSmaller < remainingAmount && actualCount < availableCount) {
                    // Thêm một tờ mệnh giá hiện tại
                    result.put(denom, actualCount + 1);
                    remainingAmount = remainingAmount - denom;
                }
            }
        }

        // Bước 4: Kiểm tra kết quả
        // Kiểm tra số tiền còn lại
        if (remainingAmount > 0) {
            throw new InvalidOperationException("ATM doesn't have enough bills to process this withdrawal");
        }

        // Xác minh tổng số tiền khớp
        int totalAmount = result.entrySet().stream()
                .mapToInt(e -> e.getKey() * e.getValue())
                .sum();
        if (totalAmount != targetAmount) {
            throw new InvalidOperationException("Internal error: Calculated amount doesn't match requested amount");
        }

        // Kiểm tra giới hạn số tờ tiền (tối đa 50 tờ)
        int totalBills = result.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBills > 50) {
            throw new InvalidOperationException(
                    "Number of bills exceeds maximum limit. Please request a smaller amount or use different denominations.");
        }

        return result;
    }

    /**
     * Hàm hỗ trợ tính toán số tiền có thể rút với các mệnh giá cho trước
     * <p>
     * Ví dụ:
     * - Cần rút: $75
     * - Mệnh giá: $20 (2 tờ), $10 (3 tờ), $5 (4 tờ)
     * -> Có thể rút: $70 ($20 x 2 + $10 x 3)
     */
    private int calculatePossibleAmount(List<Integer> denominations, Map<Integer, Integer> availableDenominations,
                                        int targetAmount) {
        int possibleAmount = 0; // Tổng số tiền có thể rút được
        int remaining = targetAmount; // Số tiền còn lại cần xử lý

        // Xử lý từng mệnh giá
        for (Integer denom : denominations) {
            // Lấy số lượng tờ có sẵn trong ATM
            int availableCount = availableDenominations.getOrDefault(denom, 0);
            if (availableCount <= 0)
                continue;

            // Tính số tờ cần thiết và có sẵn
            int neededCount = remaining / denom;
            int actualCount = Math.min(neededCount, availableCount);

            // Cộng dồn số tiền có thể rút
            possibleAmount += actualCount * denom;
            // Trừ đi số tiền đã xử lý
            remaining -= actualCount * denom;
        }

        return possibleAmount;
    }
}
