
//@Configuration
//public class KafkaConfig {
//
//    @Value("${spring.kafka.bootstrap-servers}")
//    private String bootstrapServers;
//
//    @Bean
//    public ProducerFactory<String, TransactionEvent> producerFactory() {
//        Map<String, Object> configProps = new HashMap<>();
//        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
//        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000); // 5 seconds
//        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
//        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
//        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
//        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
//
//        return new DefaultKafkaProducerFactory<>(configProps);
//    }
//
//    @Bean
//    public KafkaTemplate<String, TransactionEvent> kafkaTemplate() {
//        return new KafkaTemplate<>(producerFactory());
//    }
//
//    @Bean
//    public NewTopic transactionTopic() {
//        return TopicBuilder.name("elevate.transactions")
//                .partitions(1)
//                .replicas(1)
//                .build();
//    }
//}
package com.elevatebanking.config;

import com.elevatebanking.dto.email.EmailEvent;
import com.elevatebanking.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.elevatebanking.event.TransactionEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.groups.transaction}")
    private String transactionGroupId;

    @Value("${spring.kafka.consumer.groups.email}")
    private String emailGroupId;

    @Value("${spring.kafka.consumer.groups.notification}")
    private String notificationGroupId;

    @Value("${spring.kafka.topics.transaction}")
    private String transactionTopic;

    @Value("${spring.kafka.topics.transaction-retry}")
    private String transactionRetryTopic;

    @Value("${spring.kafka.topics.notification}")
    private String notificationTopic;

    @Value("${spring.kafka.topics.notification-retry}")
    private String notificationRetryTopic;


    // common producer config
    private <T> Map<String, Object> getProducerConfigs() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // timeout and retry settings
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000); // 5 seconds
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        // reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        return configProps;
    }

    // common consumer config

    private <T> Map<String, Object> getConsumerConfigs(Class<T> valueType, String groupId) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // consumer configuration
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // deserialization settings
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueType.getName());
        return config;
    }

    // generic producer factory builder
    private <T> ProducerFactory<String, T> buildProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getProducerConfigs());
    }

    // generic consumer factory builder
    private <T> ConsumerFactory<String, T> buildConsumerFactory(Class<T> valueType, String groupId) {
        return new DefaultKafkaConsumerFactory<>(getConsumerConfigs(valueType, groupId),
                new StringDeserializer(),
                new JsonDeserializer<>(valueType, false));
    }

    // generic listener container factory builder
    private <T> ConcurrentKafkaListenerContainerFactory<String, T> buildListenerContainerFactory(ConsumerFactory<String, T> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, ex) -> {
            log.error("Error processing message: topic={}, offset={}, key={}, value={}",
                    record.topic(), record.offset(), record.key(), record.value(), ex);
        });

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Failed to process message, attempt {} of 3. Error: {}",
                    deliveryAttempt, ex.getMessage());
        });
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // generic topic builder
    private NewTopic buildTopic(String name, int partitions, int replicas, Map<String, String> configs) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .configs(configs)
                .build();
    }

    // specific beans using generic builders
    @Bean
    public ProducerFactory<String, TransactionEvent> transactionProducerFactory() {
        return buildProducerFactory();
    }

    @Bean
    public ProducerFactory<String, NotificationEvent> notificationProducerFactory() {
        return buildProducerFactory();
    }

    @Bean
    public ProducerFactory<String, EmailEvent> emailProducerFactory() {
        return buildProducerFactory();
    }

    // template beans

    @Bean
    public KafkaTemplate<String, TransactionEvent> transactionKafkaTemplate() {
        return new KafkaTemplate<>(transactionProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate() {
        return new KafkaTemplate<>(notificationProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, EmailEvent> emailKafkaTemplate() {
        return new KafkaTemplate<>(emailProducerFactory());
    }

    // consumer factory beans

    @Bean
    public ConsumerFactory<String, TransactionEvent> transactionConsumerFactory() {
        return buildConsumerFactory(TransactionEvent.class, transactionGroupId);
    }

    @Bean
    public ConsumerFactory<String, EmailEvent> emailConsumerFactory() {
        return buildConsumerFactory(EmailEvent.class, emailGroupId);
    }

    @Bean
    public ConsumerFactory<String, NotificationEvent> notificationConsumerFactory() {
        return buildConsumerFactory(NotificationEvent.class, notificationGroupId);
    }

    // Listener container factory beans

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> transactionKafkaListenerContainerFactory() {
        return buildListenerContainerFactory(transactionConsumerFactory());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailEvent> emailKafkaListenerContainerFactory() {
        return buildListenerContainerFactory(emailConsumerFactory());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> notificationKafkaListenerContainerFactory() {
        return buildListenerContainerFactory(notificationConsumerFactory());
    }

    // Topic beans
    @Bean
    public NewTopic transactionTopic() {
        return buildTopic(transactionTopic, 4, 1, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "604800000" // 7 days
        ));
    }

    @Bean
    public NewTopic retryTopic() {
        return buildTopic(transactionRetryTopic, 4, 1, Collections.emptyMap());
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return buildTopic("elevate.transactions.dlq", 1, 1, Collections.emptyMap());
    }

    @Bean
    public NewTopic emailTopic() {
        return buildTopic("elevate.emails", 4, 1, Collections.emptyMap());
    }

    @Bean
    public NewTopic notificationTopic() {
        return buildTopic(notificationTopic, 4, 1, Map.of(
                "cleanup.policy", "delete",
                "retention.ms", "604800000" // 7 days
        ));
    }

    @Bean
    public NewTopic notificationRetryTopic() {
        return buildTopic(notificationRetryTopic, 4, 1, Collections.emptyMap());
    }

    @Bean
    public NewTopic notificationDLQTopic() {
        return buildTopic("elevate.notifications.dlq", 1, 1, Collections.emptyMap());
    }


}


//    @Bean
//    public ProducerFactory<String, NotificationEvent> notificationProducerFactory() {
//
//    }
//
//    @Bean
//    public ConsumerFactory<String, TransactionEvent> transactionConsumerFactory() {
//
//
//    }
//
//    @Bean
//    public ProducerFactory<String, TransactionEvent> producerFactory() {
//
//    }
//
//    @Bean
//    public KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate() {
//        return new KafkaTemplate<>(notificationProducerFactory());
//    }
//
//
//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> transactionKafkaListenerContainerFactory() {
//
//    }

//    @Bean
//    public KafkaTemplate<String, TransactionEvent> kafkaTemplate() {
//        return new KafkaTemplate<>(producerFactory());
//    }
//
//    @Bean
//    public NewTopic transactionTopic() {
//        return TopicBuilder.name("elevate.transactions")
//                .partitions(4) // increase partitions for better scalability
//                .replicas(1)
//                .configs(Map.of(
//                        "cleanup.policy", "delete",
//                        "retention.ms", "604800000" // 7 day
//                ))
//                .build();
//    }

//    @Bean
//    public NewTopic retryTopic() {
//        return TopicBuilder.name("elevate.transactions.retry")
//                .partitions(4)
//                .replicas(1)
//                .build();
//    }

//    @Bean
//    public NewTopic deadLetterTopic() {
//        return TopicBuilder.name("elevate.transactions.dlq")
//                .partitions(1)
//                .replicas(1)
//                .build();
//    }


//    @Bean
//    public ProducerFactory<String, EmailEvent> emailProducerFactory() {
//        Map<String, Object> configProps = new HashMap<>();
//        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
//        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
//        return new DefaultKafkaProducerFactory<>(configProps);
//    }

//    @Bean
//    public ConsumerFactory<String, EmailEvent> emailConsumerFactory() {
//        Map<String, Object> config = new HashMap<>();
//        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
//        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
//        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
//        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
//        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
//        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
//        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EmailEvent.class.getName());
//
//        return new DefaultKafkaConsumerFactory<>(config,
//                new StringDeserializer(),
//                new JsonDeserializer<>(EmailEvent.class, false));
//    }

//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, EmailEvent> emailKafkaListenerContainerFactory() {
//        ConcurrentKafkaListenerContainerFactory<String, EmailEvent> factory =
//                new ConcurrentKafkaListenerContainerFactory<>();
//        factory.setConsumerFactory(emailConsumerFactory());
//        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
//        DefaultErrorHandler errorHandler = new DefaultErrorHandler(((record, e) -> {
//            log.error("Error processing message: topic = {}, offset = {}, key = {}, value = {}",
//                    record.topic(),
//                    record.offset(),
//                    record.key(),
//                    record.value(),
//                    e);
//        }));
//
//        errorHandler.setRetryListeners(((record, ex, deliveryAttempt) -> {
//            log.warn("Failed to process email message, attempt {} of 3. Error: {}",
//                    deliveryAttempt, ex.getMessage());
//        }));
//        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
//        factory.setCommonErrorHandler(errorHandler);
//        return factory;
//    }

//    @Bean
//    public KafkaTemplate<String, EmailEvent> emailKafkaTemplate() {
//        return new KafkaTemplate<>(emailProducerFactory());
//    }

//    @Bean
//    public NewTopic emailTopic() {
//        return TopicBuilder.name("elevate.emails")
//                .partitions(4)
//                .replicas(1)
//                .build();
//    }