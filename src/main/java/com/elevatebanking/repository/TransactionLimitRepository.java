package com.elevatebanking.repository;

import com.elevatebanking.entity.enums.UserStatus;
import com.elevatebanking.entity.limit.TransactionLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, String> {
    Optional<TransactionLimit> findByUserId(String userId);

    List<TransactionLimit> findByStatus(UserStatus status);

    @Query("SELECT tl FROM TransactionLimit tl WHERE tl.user.id = :userId AND tl.status = 'ACTIVE'")
    Optional<TransactionLimit> findActiveByUserId(@Param("userId") String userId);
}
