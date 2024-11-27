package com.elevatebanking.entity.user;

import com.elevatebanking.entity.base.BaseEntity;
import com.elevatebanking.entity.base.EntityConstants;
import com.elevatebanking.entity.base.interfaces.Status;
import com.elevatebanking.entity.base.interfaces.Statusable;
import com.elevatebanking.entity.enums.TierStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


@Entity
@Table(name = "loyalty_points")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LoyaltyPoints extends BaseEntity implements Statusable {

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

    @AssertTrue(message = EntityConstants.INVALID_POINTS_CALCULATION)
    private boolean isValidPointsCalculation() {
        return totalPoints == pointsEarned - pointsSpent;
    }


    @Override
    public Status getStatus() {
        return tierStatus;
    }

    @Override
    public void setStatus(Status status) {
        if (status instanceof TierStatus) {
            this.tierStatus = (TierStatus) status;
        } else {
            throw new IllegalArgumentException("Status must be TierStatus");
        }
    }
}

