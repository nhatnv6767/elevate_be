package com.elevatebanking.entity.notification;

import com.elevatebanking.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_channels")
@Getter
@Setter
@NoArgsConstructor
public class NotificationChannel extends BaseEntity {

    @NotBlank(message = "Channel code is required")
    @Column(name = "channel_code", unique = true, nullable = false)
    private String channelCode;

    @NotBlank(message = "Channel name is required")
    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "configuration", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String configuration;
}
