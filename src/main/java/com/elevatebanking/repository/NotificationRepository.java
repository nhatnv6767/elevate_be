package com.elevatebanking.repository;

import com.elevatebanking.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByUserId(String userId);

    List<Notification> findByUserIdAndIsRead(String userId, boolean isRead);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.isRead = false")
    Long countUnreadNotifications(@Param("userId") String userId);

    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId")
    @Modifying
    void markAllAsRead(@Param("userId") String userId);
}
