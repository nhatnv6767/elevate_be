package com.elevatebanking.util;

import com.elevatebanking.entity.enums.BankType;
import com.elevatebanking.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Component
@Slf4j
public class AccountNumberGenerator {

    //    private static final String BRANCH_CODE = "001";
    private final Random random = new Random();
    private final AccountRepository accountRepository;

    @Autowired
    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public String generate(BankType bankType) {
        String accountNumber;
        int maxAttempts = 5;
        int attempts = 0;

        do {
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String randomStr = String.format("%05d", random.nextInt(100000));
            accountNumber = bankType.getBranchCode() + dateStr + randomStr;
            attempts++;

            if (attempts >= maxAttempts) {
                log.error("Failed to generate unique account number after {} attempts", maxAttempts);
                throw new RuntimeException("Unable to generate unique account number");
            }
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }
}
