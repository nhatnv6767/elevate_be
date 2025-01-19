package com.elevatebanking.event;

import java.util.Collections;
import java.util.Set;

public enum EmailType {
    PASSWORD_RESET(
            "Reset Your Password - Elevate Banking",
            "email/reset-password",
            Set.of("username", "token")
    ),
    TRANSACTION(
            "Transaction Notification",
            "email/transaction-notification",
            Set.of("subject", "message", "bankName", "supportEmail")
    ),
    SYSTEM_ALERT(
            "System Alert",
            "email/system-alert",
            Set.of("subject", "message", "bankName")
    );
    private final String defaultSubject;
    private final String templatePath;
    private final Set<String> requiredTemplateFields;

    EmailType(String defaultSubject, String templatePath, Set<String> requiredTemplateFields) {
        this.defaultSubject = defaultSubject;
        this.templatePath = templatePath;
        this.requiredTemplateFields = Collections.unmodifiableSet(requiredTemplateFields);
    }

    public String getDefaultSubject() {
        return defaultSubject;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public Set<String> getRequiredTemplateFields() {
        return requiredTemplateFields;
    }
}
