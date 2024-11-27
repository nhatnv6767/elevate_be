package com.elevatebanking.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "billers")
@Getter
@Setter
@NoArgsConstructor
public class Biller {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "biller_id", columnDefinition = "VARCHAR(36)")
    private String id;

    @NotBlank(message = "Biller name is required")
    @Size(min = 2, max = 100, message = "Biller name must be between 2 and 100 characters")
    @Column(name = "biller_name", length = 100, nullable = false)
    private String billerName;

    @NotBlank(message = "Category is required")
    @Size(min = 2, max = 50, message = "Category must be between 2 and 50 characters")
    @Column(length = 50, nullable = false)
    private String category;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillerStatus status = BillerStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}


