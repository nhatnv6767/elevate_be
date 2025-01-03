package com.elevatebanking.repository;

import com.elevatebanking.entity.limit.LimitHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimitHistoryRepository extends JpaRepository<LimitHistory, String> {
    List<LimitHistory> findByTransactionLimitId(String limitId);

    List<LimitHistory> findByChangedBy(String changedBy);

    @Query("SELECT lh FROM LimitHistory lh WHERE lh.transactionLimit.user.id = :userId order by lh.createdAt desc")
    List<LimitHistory> findUserLimitHistory(@Param("userId") String userId);
}
