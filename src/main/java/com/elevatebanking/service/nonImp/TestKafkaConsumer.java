package com.elevatebanking.service.nonImp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class TestKafkaConsumer {
    @KafkaListener(topics = "elevate.test", groupId = "test-group")
    public void handleTestMessage(Map<String, String> message) {
        log.info("Received test message: {}", message);
    }
}
