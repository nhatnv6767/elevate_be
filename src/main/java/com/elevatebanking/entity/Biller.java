package com.elevatebanking.entity;

import jakarta.persistence.*;
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

    @Column(name = "biller_name", nullable = false, length = 100)
    private String billerName;

    @Column(length = 50, nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillerStatus status = BillerStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}


enum BillerStatus {
    ACTIVE, INACTIVE
}