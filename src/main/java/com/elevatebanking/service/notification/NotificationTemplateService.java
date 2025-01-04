package com.elevatebanking.service.notification;

import com.elevatebanking.entity.notification.NotificationTemplate;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.NotificationTemplateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationTemplateService {
    NotificationTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public NotificationTemplate getTemplate(String templateCode) {
        return templateRepository.findByTemplateCode(templateCode)
                .orElseThrow(() -> new ResourceNotFoundException("Notification template not found for code: " + templateCode));
    }

    @Transactional
    public NotificationTemplate createTemplate(NotificationTemplate template) {
        validateTemplate(template);
        return templateRepository.save(template);
    }

    @Transactional
    public NotificationTemplate updateTemplate(String templateCode, NotificationTemplate template) {
        NotificationTemplate existing = getTemplate(templateCode);
        updateTemplateFields(existing, template);
        return templateRepository.save(existing);
    }

    @Transactional
    public void deactivateTemplate(String templateCode) {
        NotificationTemplate template = getTemplate(templateCode);
        template.setActive(false);
        templateRepository.save(template);
    }

    public String renderTemplate(NotificationTemplate template, Map<String, Object> data) {
        String content = template.getBodyTemplate();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            content = content.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return content;
    }

    void validateTemplate(NotificationTemplate template) {
        if (template.getTemplateCode() == null || template.getTemplateCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Template code is required");
        }
        if (template.getBodyTemplate() == null || template.getBodyTemplate().trim().isEmpty()) {
            throw new IllegalArgumentException("Template body is required");
        }
    }

    void updateTemplateFields(NotificationTemplate existing, NotificationTemplate updated) {
        existing.setTemplateName(updated.getTemplateName());
        existing.setSubjectTemplate(updated.getSubjectTemplate());
        existing.setBodyTemplate(updated.getBodyTemplate());
        existing.setNotificationType(updated.getNotificationType());
        existing.setActive(updated.isActive());
    }
}
