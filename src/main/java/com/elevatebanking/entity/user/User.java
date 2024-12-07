package com.elevatebanking.entity.user;

import com.elevatebanking.entity.base.interfaces.Status;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.base.AuditableEntity;
import com.elevatebanking.entity.base.EntityConstants;
import com.elevatebanking.entity.base.interfaces.Statusable;
import com.elevatebanking.entity.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User extends AuditableEntity implements Statusable {

    @NotBlank(message = EntityConstants.REQUIRED_FIELD)
    @Size(min = EntityConstants.USERNAME_MIN_LENGTH, max = EntityConstants.USERNAME_MAX_LENGTH)
    @Pattern(regexp = EntityConstants.USERNAME_PATTERN)
    @Column(unique = true, nullable = false, length = EntityConstants.USERNAME_MAX_LENGTH)
    private String username;

    @NotBlank(message = EntityConstants.REQUIRED_FIELD)
    @Pattern(regexp = EntityConstants.PASSWORD_PATTERN, message = EntityConstants.INVALID_PASSWORD)
    @Column(nullable = false)
    private String password;

    @NotBlank(message = EntityConstants.REQUIRED_FIELD)
    @Size(min = EntityConstants.NAME_MIN_LENGTH, max = EntityConstants.NAME_MAX_LENGTH)
    @Column(name = "full_name", nullable = false, length = EntityConstants.NAME_MAX_LENGTH)
    private String fullName;

    @NotBlank(message = EntityConstants.REQUIRED_FIELD)
    @Email(regexp = EntityConstants.EMAIL_PATTERN, message = EntityConstants.INVALID_EMAIL)
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank(message = EntityConstants.REQUIRED_FIELD)
    @Pattern(regexp = EntityConstants.PHONE_PATTERN, message = EntityConstants.INVALID_PHONE)
    @Column(unique = true, nullable = false, length = EntityConstants.PHONE_MAX_LENGTH)
    private String phone;

    @NotNull(message = EntityConstants.REQUIRED_FIELD)
    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @NotBlank(message = EntityConstants.REQUIRED_FIELD)
    @Pattern(regexp = EntityConstants.IDENTITY_NUMBER_PATTERN)
    @Column(name = "identity_number", unique = true, nullable = false, length = 20)
    private String identityNumber;

    @NotNull(message = EntityConstants.REQUIRED_FIELD)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @NotNull(message = "User must have at least one role")
    @Size(min = 1, message = "User must have at least one role")
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private List<Role> roles = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Account> accounts = new HashSet<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private LoyaltyPoints loyaltyPoints;

    @Override
    public void setStatus(Status status) {
        if (status instanceof UserStatus) {
            this.status = (UserStatus) status;
        } else {
            throw new IllegalArgumentException("Status must be UserStatus");
        }
    }

    // Helper methods
    public void addRole(Role role) {
        roles.add(role);
        role.getUsers().add(this);
    }

    public void removeRole(Role role) {
        roles.remove(role);
        role.getUsers().remove(this);
    }

    public void addAccount(Account account) {
        accounts.add(account);
        account.setUser(this);
    }

    public void removeAccount(Account account) {
        accounts.remove(account);
        account.setUser(null);
    }

    @PrePersist
    protected void onCreate() {
        if (loyaltyPoints == null) {
            loyaltyPoints = new LoyaltyPoints();
            loyaltyPoints.setUser(this);
        }
    }
}
