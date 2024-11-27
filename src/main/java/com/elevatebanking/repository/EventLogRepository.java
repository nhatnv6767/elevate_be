package com.elevatebanking.repository;

import com.elevatebanking.entity.enums.EventStatus;
import com.elevatebanking.entity.log.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, String> {
    List<EventLog> findByStatus(EventStatus status);

    List<EventLog> findByTopic(String topic);

    @Query("SELECT el FROM EventLog el WHERE el.status = 'PENDING' AND el.retryCount < :maxRetries")
    List<EventLog> findRetryableEvents(@Param("maxRetries") int maxRetries);

    @Query("SELECT el FROM EventLog el WHERE el.status = 'PENDING' AND el.createdAt < :timeout")
    List<EventLog> findStaleEvents(@Param("timeout") LocalDateTime timeout);
}
