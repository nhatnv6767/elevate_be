package com.elevatebanking.repository;

import com.elevatebanking.entity.notification.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, String> {
    Optional<NotificationChannel> findByChannelCode(String channelCode);

    List<NotificationChannel> findByActive(boolean active);

    @Query("SELECT nc FROM NotificationChannel nc WHERE nc.active = true AND nc.channelCode IN :channelCodes")
    List<NotificationChannel> findActiveChannelsByCodes(@Param("channelCodes") List<String> channelCodes);
}
