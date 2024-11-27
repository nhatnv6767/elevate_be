package com.elevatebanking.entity;

import jakarta.persistence.*;
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    @Column(name = "points_earned", nullable = false)
    private Integer pointsEarned = 0;

    @Column(name = "points_spent", nullable = false)
    private Integer pointsSpent = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier_status")
    private TierStatus tierStatus = TierStatus.BRONZE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


}

enum TierStatus {
    BRONZE, SILVER, GOLD, PLATINUM
}
