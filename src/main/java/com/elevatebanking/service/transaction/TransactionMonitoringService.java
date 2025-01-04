package com.elevatebanking.service.transaction;

import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.event.NotificationEvent;
import com.elevatebanking.repository.TransactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TransactionMonitoringService {
    TransactionRepository transactionRepository;
    KafkaTemplate<String, NotificationEvent> notificationTemplate;

    @Value("${spring.kafka.topics.notification}")
    @NonFinal
    private String notificationTopic;

    @Scheduled(fixedRate = 60000) // 1 minute
    public void monitorTransactionMetrics() {
        log.info("Starting transaction monitoring process");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        // monitor success rate
        double successRate = calculateSuccessRate(oneHourAgo, now);
        if (successRate < 0.95) { // alert if success rate drops below 95%
            sendAlertNotification("Transaction success rate dropped to " + String.format("%.2f", successRate * 100) + "%");
        }

        // monitor processing time
        double avgProcessingTime = calculateAverageProcessingTime(oneHourAgo, now);
        if (avgProcessingTime > 5000) { // alert if average processing time exceeds 5 seconds
            sendAlertNotification("High transaction processing time: " + String.format("%.2f ms", avgProcessingTime));
        }

        // monitor failed transactions
        List<Transaction> failedTransactions = findRecentFailedTransactions(oneHourAgo);
        if (failedTransactions.size() > 10) { // alert if more than 10 failures in an hour
            sendAlertNotification("High number of failed transactions: " + failedTransactions.size());
        }


    }

    double calculateSuccessRate(LocalDateTime start, LocalDateTime end) {
        long totalCount = transactionRepository.countTransactionsInTimeframe(start, end);
        long successCount = transactionRepository.countTransactionsByStatusInTimeframe(TransactionStatus.COMPLETED, start, end);
        // return success rate as a percentage
        return totalCount > 0 ? (double) successCount / totalCount : 1.0;

    }

    double calculateAverageProcessingTime(LocalDateTime start, LocalDateTime end) {
        return transactionRepository.calculateAverageProcessingTime(start, end);
    }

    List<Transaction> findRecentFailedTransactions(LocalDateTime since) {
        return transactionRepository.findByStatusAndCreatedAtAfter(TransactionStatus.FAILED, since);
    }

    void sendAlertNotification(String message) {
        NotificationEvent alert = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("SYSTEM_ALERT")
                .priority(NotificationEvent.Priority.HIGH.name())
                .title("Transaction Monitoring Alert")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        notificationTemplate.send(notificationTopic, alert.getEventId(), alert);
    }
}
