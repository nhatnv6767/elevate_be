package com.elevatebanking.entity;

import com.elevatebanking.entity.enums.BeneficiaryStatus;
import com.elevatebanking.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{10,20}$", message = "Invalid account number format")
    @Column(name = "account_number", length = 20, nullable = false)
    private String accountNumber;

    @Size(min = 2, max = 100, message = "Bank name must be between 2 and 100 characters")
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @NotBlank(message = "Beneficiary name is required")
    @Size(min = 2, max = 100, message = "Beneficiary name must be between 2 and 100 characters")
    @Column(name = "beneficiary_name", length = 100, nullable = false)
    private String beneficiaryName;

    @Size(min = 2, max = 50, message = "Nickname must be between 2 and 50 characters")
    @Column(length = 50)
    private String nickname;

    @Column(name = "is_favorite")
    private Boolean isFavorite = false;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BeneficiaryStatus status = BeneficiaryStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}


