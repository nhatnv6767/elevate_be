package com.elevatebanking.service.nonImp;

import com.elevatebanking.event.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class TestKafkaConsumer {
    @KafkaListener(topics = "elevate.test", groupId = "test-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleTestMessage(TransactionEvent message) {
        log.info("Received test message: {}", message);
    }
}
