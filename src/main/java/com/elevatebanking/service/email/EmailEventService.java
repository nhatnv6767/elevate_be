package com.elevatebanking.service.email;

import com.elevatebanking.event.EmailEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailEventService {
    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

    public EmailEventService(KafkaTemplate<String, EmailEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEmailEvent(EmailEvent emailEvent) {
        log.info("Emitting email event with deduplication ID: {}", emailEvent.getDeduplicationId());
        kafkaTemplate.send("elevate.emails", emailEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to emit email event: {}", ex.getMessage());
                    } else {
                        log.info("Email event emitted successfully");
                    }
                });
    }
}
