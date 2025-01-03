package com.elevatebanking.repository;

import com.elevatebanking.entity.notification.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {
    Optional<NotificationPreference> findByUserId(String userId);

    @Query("SELECT np FROM NotificationPreference np WHERE np.emailEnabled = true")
    List<NotificationPreference> findAllWithEmailEnabled();

    @Query("SELECT np FROM NotificationPreference np WHERE np.pushEnabled = true")
    List<NotificationPreference> findAllWithPushEnabled();

    @Query("SELECT np FROM NotificationPreference np WHERE np.smsEnabled = true")
    List<NotificationPreference> findAllWithSmsEnabled();
}
