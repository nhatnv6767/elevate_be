package com.elevatebanking.service.nonImp;

import com.elevatebanking.entity.log.AuditLog;
import com.elevatebanking.entity.user.User;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogService {
    AuditLogRepository auditLogRepository;
    ObjectMapper objectMapper;
    HttpServletRequest request;

    @Transactional(rollbackFor = Exception.class)
    public void logEvent(String userId, String action, String entityType, String entityId, Object previousState, Object currentState) {
        try {
            if (userId == null) {
                log.warn("Cannot create audit log without user ID");
                return;
            }


            AuditLog auditLog = new AuditLog();
            auditLog.setId(UUID.randomUUID().toString());
            auditLog.setUser(createUserRef(userId));
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);

            // capture context information
            auditLog.setIpAddress(getClientIpAddress());
            auditLog.setUserAgent(request.getHeader("User-Agent"));
            setAuditStates(auditLog, previousState, currentState);

            String details = generateAuditDetails(previousState, currentState);
            auditLog.setDetails(details);
            auditLog.setStatus(AuditLog.AuditStatus.SUCCESS);
            auditLog.setVersion(0L);
            auditLogRepository.save(auditLog);
            log.info("Audit log created for action: {} on entity: {}", action, entityId);
        } catch (Exception e) {
            log.error("Failed to create audit log for action: {} on entity: {}", action, entityId, e);
            throw new RuntimeException("Failed to create audit log", e);
        }
    }

    private User createUserRef(String userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    private void setAuditStates(AuditLog auditLog, Object previousState, Object currentState) {
        try {
            if (previousState != null) {
                auditLog.setPreviousState(objectMapper.writeValueAsString(previousState));
            }
            if (currentState != null) {
                auditLog.setCurrentState(objectMapper.writeValueAsString(currentState));
            }
        } catch (Exception e) {
            log.error("Lỗi serialize state: {}", e.getMessage());
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

        try {
            String[] headers = {
                    "X-Forwarded-For",
                    "Proxy-Client-IP",
                    "WL-Proxy-Client-IP",
                    "HTTP_X_FORWARDED_FOR",
                    "HTTP_X_FORWARDED",
                    "HTTP_X_CLUSTER_CLIENT_IP",
                    "HTTP_CLIENT_IP",
                    "HTTP_FORWARDED_FOR",
                    "HTTP_FORWARDED",
                    "HTTP_VIA",
                    "REMOTE_ADDR"
            };
            for (String header : headers) {
                String ip = request.getHeader(header);
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    // if have multiple ip addresses, return the first one
                    if (ip.contains(",")) {
                        ip = ip.split(",")[0].trim();
                    }
                    return ip;
                }
            }
            // fallback to remote address
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.error("Failed to get client IP address: {}", e.getMessage());
            return "0.0.0.0";
        }
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
