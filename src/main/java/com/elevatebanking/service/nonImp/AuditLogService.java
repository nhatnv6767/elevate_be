package com.elevatebanking.service.nonImp;

import com.elevatebanking.entity.log.AuditLog;
import com.elevatebanking.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogService {
    AuditLogRepository auditLogRepository;
    ObjectMapper objectMapper;
    HttpServletRequest request;

    @Transactional
    public void logEvent(String userId, String action, String entityType, String entityId, Object previousState, Object currentState) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setId(userId);
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);

            if (previousState != null) {
                auditLog.setPreviousState(objectMapper.writeValueAsString(previousState));
            }
            if (currentState != null) {
                auditLog.setCurrentState(objectMapper.writeValueAsString(currentState));
            }
            // capture context information
            auditLog.setIpAddress(getClientIpAddress());
            auditLog.setUserAgent(request.getHeader("User-Agent"));

            String details = generateAuditDetails(previousState, currentState);
            auditLog.setDetails(details);
            auditLog.setStatus(AuditLog.AuditStatus.SUCCESS);
            auditLogRepository.save(auditLog);
            log.info("Audit log created for action: {} on entity: {}", action, entityId);
        } catch (Exception e) {
            log.error("Failed to create audit log for action: {} on entity: {}", action, entityId, e);
            throw new RuntimeException("Failed to create audit log", e);
        }
    }

    String generateAuditDetails(Object previousState, Object currentState) {
        try {
            if (previousState == null || currentState == null) {
                return null;
            }

            Map<String, Object> details = new HashMap<>();
            Map<String, Object> previous = objectMapper.convertValue(previousState, Map.class);
            Map<String, Object> current = objectMapper.convertValue(currentState, Map.class);

            // find changed fields
            Map<String, ChangedValue> changes = new HashMap<>();
            for (String key : current.keySet()) {
                Object prevValue = previous.get(key);
                Object currValue = current.get(key);
                if (!Objects.equals(prevValue, currValue)) {
                    changes.put(key, new ChangedValue(prevValue, currValue));
                }
            }

            details.put("changes", changes);
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            log.error("Failed to generate audit details", e);
            return null;
        }
    }

    String getClientIpAddress() {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteUser();
    }

    @Data
    @AllArgsConstructor
    static class ChangedValue {
        private Object oldValue;
        private Object newValue;
    }


    public List<AuditLog> getActivityHistory(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        return auditLogRepository.findByDateRange(startDate, endDate).stream()
                .filter(log -> log.getUser().getId().equals(userId))
                .collect(Collectors.toList());
    }

    public List<AuditLog> getEntityHistory(String entityType, String entityId) {
        return auditLogRepository.findByEntity(entityType, entityId);
    }

    public Map<String, Long> getUserActionSummary(String userId, LocalDateTime since) {
        return auditLogRepository.findByUserId(userId).stream()
                .filter(log -> log.getCreatedAt().isAfter(since))
                .collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));
    }
}
