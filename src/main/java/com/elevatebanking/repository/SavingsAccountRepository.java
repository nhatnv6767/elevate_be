package com.elevatebanking.repository;

import com.elevatebanking.entity.account.SavingsAccount;
import com.elevatebanking.entity.enums.SavingsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, String> {
    List<SavingsAccount> findByAccountId(String accountId);

    List<SavingsAccount> findByStatus(SavingsStatus status);

    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.status = 'ACTIVE' AND sa.maturityDate <= :date")
    List<SavingsAccount> findMaturityDueAccounts(@Param("date") LocalDate date);

    @Query("SELECT SUM(sa.principalAmount) FROM SavingsAccount sa WHERE sa.account.id = :accountId")
    BigDecimal getTotalSavingsByAccountId(@Param("accountId") String accountId);

    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.account.user.id = :userId")
    List<SavingsAccount> findByUserId(@Param("userId") String userId);
}
