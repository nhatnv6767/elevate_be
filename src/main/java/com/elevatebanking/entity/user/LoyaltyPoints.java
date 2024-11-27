package com.elevatebanking.entity.user;

import com.elevatebanking.entity.User;
import com.elevatebanking.entity.enums.TierStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_points")
@Getter
@Setter
@NoArgsConstructor
public class LoyaltyPoints {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "point_id", columnDefinition = "VARCHAR(36)")
    private String id;

    @NotNull(message = "User is required")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Total points is required")
    @PositiveOrZero(message = "Total points cannot be negative")
    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    @NotNull(message = "Points earned is required")
    @PositiveOrZero(message = "Points earned cannot be negative")
    @Column(name = "points_earned", nullable = false)
    private Integer pointsEarned = 0;

    @NotNull(message = "Points spent is required")
    @PositiveOrZero(message = "Points spent cannot be negative")
    @Column(name = "points_spent", nullable = false)
    private Integer pointsSpent = 0;

    @NotNull(message = "Tier status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "tier_status")
    private TierStatus tierStatus = TierStatus.BRONZE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @AssertTrue(message = "Invalid points calculation")
    private boolean isValidPointsCalculation() {
        return totalPoints == pointsEarned - pointsSpent;
    }
}

