package com.elevatebanking.service.email;

import com.elevatebanking.event.EmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailEventService {
    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

//    public EmailEventService(KafkaTemplate<String, EmailEvent> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }

    public void sendEmailEvent(EmailEvent emailEvent) {
        try {
            // pre-send validation
            log.debug("Processing email event: {}", emailEvent.debugInfo());
            emailEvent.validate();

            // logging before send
            emailEvent.addProcessStep("SENDING");
            log.debug("Validated email event successfully: {}", emailEvent.debugInfo());
            log.info("Emitting email event: id={}, type={}, to={}, deduplicationId={}",
                    emailEvent.getEventId(), emailEvent.getType(), emailEvent.getTo(), emailEvent.getDeduplicationId());

            kafkaTemplate.send("elevate.emails", emailEvent.getEventId(), emailEvent)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            emailEvent.addProcessStep("SEND_FAILED: " + ex.getMessage());
                            log.error("Failed to emit email event: id={}, error={}",
                                    emailEvent.getEventId(), ex.getMessage(), ex);
                        } else {
                            emailEvent.addProcessStep("SENT");
                            log.info("Email event emitted successfully: id={}, partition={}",
                                    emailEvent.getEventId(),
                                    result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception e) {
            emailEvent.addProcessStep("ERROR: " + e.getMessage());
            log.error("Error processing email event: id={}, error={}", emailEvent.getEventId(), e.getMessage(), e);
            throw e;
        }
    }
}
