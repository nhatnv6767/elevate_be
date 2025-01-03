package com.elevatebanking.entity.notification;

import com.elevatebanking.entity.base.BaseEntity;
import com.elevatebanking.event.NotificationEvent.NotificationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplate extends BaseEntity {

    @NotBlank(message = "Template code is required")
    @Column(name = "template_code", unique = true, nullable = false)
    private String templateCode;

    @NotBlank(message = "Template name is required")
    @Column(name = "template_name", nullable = false)
    private String templateName;

    @NotBlank(message = "Template subject is required")
    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;

    @NotBlank(message = "Template body is required")
    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(name = "is_active")
    private boolean active = true;

}
