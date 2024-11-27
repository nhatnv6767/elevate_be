package com.elevatebanking.repository;

import com.elevatebanking.entity.enums.PointTransactionType;
import com.elevatebanking.entity.transaction.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, String> {
    List<PointTransaction> findByUserId(String userId);

    List<PointTransaction> findByUserIdAndType(String userId, PointTransactionType type);

    @Query("SELECT SUM(pt.points) FROM PointTransaction pt WHERE pt.user.id = :userId AND pt.type = :type")
    Integer sumPointsByUserAndType(@Param("userId") String userId, @Param("type") PointTransactionType type);
}
