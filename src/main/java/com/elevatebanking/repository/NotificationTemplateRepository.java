package com.elevatebanking.repository;

import com.elevatebanking.entity.notification.NotificationTemplate;
import com.elevatebanking.event.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> {
    Optional<NotificationTemplate> findByTemplateCode(String templateCode);

    List<NotificationTemplate> findByNotificationType(NotificationEvent.NotificationType type);

    List<NotificationTemplate> findByActive(boolean active);

    @Query("SELECT nt FROM NotificationTemplate nt WHERE nt.notificationType = :type AND nt.active = true")
    Optional<NotificationTemplate> findActiveTemplateByType(@Param("type") NotificationEvent.NotificationType type);
}
