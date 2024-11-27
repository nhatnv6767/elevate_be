package com.elevatebanking.repository;

import com.elevatebanking.entity.enums.TierStatus;
import com.elevatebanking.entity.user.LoyaltyPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyPointsRepository extends JpaRepository<LoyaltyPoints, String> {
    Optional<LoyaltyPoints> findByUserId(String userId);

    List<LoyaltyPoints> findByTierStatus(TierStatus tierStatus);

    @Query("SELECT lp FROM LoyaltyPoints lp WHERE lp.totalPoints >= :points")
    List<LoyaltyPoints> findByMinimumPoints(@Param("points") Integer points);

    @Query(value = "SELECT * FROM loyalty_points order by total_points desc limit :limit", nativeQuery = true)
    List<LoyaltyPoints> findTopPointHolders(@Param("limit") int limit);
}
