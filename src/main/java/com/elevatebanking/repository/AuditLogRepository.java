package com.elevatebanking.repository;

import com.elevatebanking.entity.log.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByUserId(String userId);

    List<AuditLog> findByAction(String action);


    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :startDate AND :endDate")
    List<AuditLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT al FROM AuditLog al WHERE al.entityType = :entityType AND al.entityId = :entityId")
    List<AuditLog> findByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);

    @Query("SELECT al FROM AuditLog al WHERE al.user.id = :userId AND al.entityType = :entityType")
    List<AuditLog> findByUserAndEntityType(@Param("userId") String userId, @Param("entityType") String entityType);

    @Query("SELECT al FROM AuditLog al WHERE al.status = :status AND al.createdAt >= :since")
    List<AuditLog> findByStatusAndCreatedAfter(
            @Param("status") AuditLog.AuditStatus status,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.user.id = :userId AND al.action = :action AND al.createdAt >= :since")
    long countUserActionInPeriod(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("since") LocalDateTime since
    );

    @Query("select distinct al.entityType from AuditLog al where al.entityType =:entityType and al.createdAt between :startDate and :endDate")
    List<String> findModifiedEntitiesInPeriod(
            @Param("entityType") String entityType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
