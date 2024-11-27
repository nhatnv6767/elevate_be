package com.elevatebanking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@NoArgsConstructor
public class Beneficiary {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "beneficiary_id", columnDefinition = "VARCHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_number", length = 20, nullable = false)
    private String accountNumber;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "beneficiary_name", length = 100, nullable = false)
    private String beneficiaryName;

    @Column(length = 50)
    private String nickname;

    @Column(name = "is_favorite")
    private Boolean isFavorite = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BeneficiaryStatus status = BeneficiaryStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}

enum BeneficiaryStatus {
    ACTIVE, INACTIVE
}
