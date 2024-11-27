package com.elevatebanking.repository;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    List<Account> findByUserId(String userId);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.status = :status")
    List<Account> findAccountsByUserIdAndStatus(@Param("userId") String userId, @Param("status") AccountStatus status);

    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.user.id = :userId")
    BigDecimal getTotalBalanceByUserId(@Param("userId") String userId);
}
