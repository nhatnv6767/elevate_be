package com.elevatebanking.entity.notification;

import com.elevatebanking.entity.base.BaseEntity;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.event.NotificationEvent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "email_enabled")
    private boolean emailEnabled = true;

    @Column(name = "push_enabled")
    private boolean pushEnabled = true;

    @Column(name = "sms_enabled")
    private boolean smsEnabled = false;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "notification_types_enabled", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "notification_type")
    private Set<NotificationEvent.NotificationType> enabledTypes = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "notification_channels_enabled",
            joinColumns = @JoinColumn(name = "preference_id"),
            inverseJoinColumns = @JoinColumn(name = "channel_id"))
//    @Column(name = "channel")
    private Set<NotificationChannel> enabledChannels = new HashSet<>();
}
