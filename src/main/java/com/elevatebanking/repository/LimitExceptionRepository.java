package com.elevatebanking.repository;

import com.elevatebanking.entity.limit.LimitException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LimitExceptionRepository extends JpaRepository<LimitException, String> {
    List<LimitException> findByUserId(String userId);

    List<LimitException> findByActive(boolean active);

    @Query("SELECT le FROM LimitException le WHERE le.user.id = :userId AND le.active = true and le.startTime <= CURRENT_TIMESTAMP and le.endTime >= CURRENT_TIMESTAMP")
    Optional<LimitException> findActiveExceptionForUser(@Param("userId") String userId);
}
