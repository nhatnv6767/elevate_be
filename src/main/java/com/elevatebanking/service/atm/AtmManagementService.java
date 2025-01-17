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

    /*
     * Hàm calculateOptimalDenominations() tính toán số lượng tờ tiền tối ưu
     * cho một giao dịch rút tiền ATM.
     *
     * Luồng xử lý chính:
     *
     * 1. Input parameters:
     * - atmId: ID của máy ATM
     * - amount: Số tiền cần rút
     *
     * 2. Kiểm tra điều kiện đầu vào:
     * - Lấy thông tin ATM từ ID
     * - Lấy danh sách mệnh giá tiền có trong ATM
     * - Kiểm tra số tiền rút phải là số nguyên dương
     *
     * 3. Xử lý tính toán mệnh giá:
     * - Sắp xếp các mệnh giá theo thứ tự giảm dần
     * - Với mỗi mệnh giá, tính số lượng tờ cần thiết:
     * + Số lượng tờ = Số tiền còn lại / mệnh giá
     * + So sánh với số lượng tờ có sẵn trong ATM
     * + Chọn số lượng tờ nhỏ hơn giữa 2 giá trị trên
     * + Cập nhật số tiền còn lại cần rút
     *
     * 4. Tối ưu hóa mệnh giá:
     * - Nếu mệnh giá hiện tại > 50 và còn tiền chưa rút hết
     * - Tính tổng các mệnh giá nhỏ hơn còn lại
     * - Nếu tổng > 1/2 mệnh giá hiện tại
     * - Thử rút thêm 1 tờ mệnh giá hiện tại
     *
     * 5. Kiểm tra điều kiện kết quả:
     * - Đảm bảo rút hết được số tiền yêu cầu
     * - Tổng số tờ tiền không vượt quá 50 tờ
     *
     * 6. Output:
     * - Trả về Map<Integer, Integer> chứa:
     * + Key: mệnh giá tiền
     * + Value: số lượng tờ tương ứng
     *
     * 7. Exception handling:
     * - Throw InvalidOperationException nếu:
     * + Số tiền không hợp lệ
     * + ATM không đủ tiền
     * + Vượt quá giới hạn số tờ tiền
     */
    @Transactional
    public Map<Integer, Integer> calculateOptimalDenominations(String atmId, BigDecimal amount) {
        // Lấy thông tin ATM
        AtmMachine atm = getAtmById(atmId);
        Map<Integer, Integer> availableDenominations = atm.getDenominations();
        Map<Integer, Integer> result = new HashMap<>();

        // Kiểm tra số tiền phải là số nguyên
        if (amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidOperationException("Amount must be greater than 0");
        }

        int targetAmount = amount.intValue();
        int remainingAmount = targetAmount;

        // Sắp xếp mệnh giá giảm dần
        List<Integer> denominations = new ArrayList<>(availableDenominations.keySet());
        denominations.sort(Collections.reverseOrder());

        // Xử lý từng mệnh giá
        for (int i = 0; i < denominations.size(); i++) {
            Integer denom = denominations.get(i);
            if (remainingAmount <= 0)
                break;

            int availableCount = availableDenominations.getOrDefault(denom, 0);
            if (availableCount <= 0)
                continue;

            // Tính số lượng tờ cần thiết cho mệnh giá này
            int neededCount = remainingAmount / denom;
            // Lấy số nhỏ hơn giữa số tờ cần và số tờ có sẵn
            int actualCount = Math.min(neededCount, availableCount);

            if (actualCount > 0) {
                result.put(denom, actualCount);
                remainingAmount -= actualCount * denom;
            }

            // Tối ưu hóa với mệnh giá lớn
            if (remainingAmount > 0 && i < denominations.size() - 1) {
                // tinh xem co the rut duoc bao nhieu voi cac menh gia nho hon
                int possibleWithSmaller = calculatePossibleAmount(
                        denominations.subList(i + 1, denominations.size()),
                        availableDenominations,
                        remainingAmount
                );

                if (possibleWithSmaller < remainingAmount && actualCount < availableCount) {
                    // thu them 1 to voi menh gia hien tai
                    result.put(denom, actualCount + 1);
                    remainingAmount = remainingAmount - denom;
                }
            }
        }


        if (remainingAmount > 0) {
            throw new InvalidOperationException("ATM doesn't have enough bills to process this withdrawal");
        }

        int totalAmount = result.entrySet().stream()
                .mapToInt(e -> e.getKey() * e.getValue())
                .sum();
        if (totalAmount != targetAmount) {
            throw new InvalidOperationException("Internal error: Calculated amount doesn't match requested amount");
        }

        // kiem tra so luong to tien
        int totalBills = result.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBills > 50) {
            throw new InvalidOperationException(
                    "Number of bills exceeds maximum limit. Please request a smaller amount or use different denominations.");
        }

        return result;
    }

    
    private int calculatePossibleAmount(List<Integer> denominations, Map<Integer, Integer> availableDenominations, int targetAmount) {
        int possibleAmount = 0;
        int remaining = targetAmount;

        for (Integer denom : denominations) {
            int availableCount = availableDenominations.getOrDefault(denom, 0);
            if (availableCount <= 0) continue;

            int neededCount = remaining / denom;
            int actualCount = Math.min(neededCount, availableCount);

            possibleAmount += actualCount * denom;
            remaining -= actualCount * denom;
        }

        return possibleAmount;
    }
}
