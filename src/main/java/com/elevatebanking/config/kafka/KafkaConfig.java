
package com.elevatebanking.config.kafka;

import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.event.NotificationEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
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
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.elevatebanking.event.TransactionEvent;

import java.util.*;

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

    @Value("${spring.kafka.topics.email}")
    private String emailTopic;

    @Value("${spring.kafka.topics.email-retry}")
    private String emailRetryTopic;


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
//        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

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
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
//        return new DefaultKafkaConsumerFactory<>(props);
//        return buildConsumerFactory(TransactionEvent.class, transactionGroupId);
        return new DefaultKafkaConsumerFactory<>(getConsumerConfigs(TransactionEvent.class, transactionGroupId),
                new StringDeserializer(),
                new JsonDeserializer<>(TransactionEvent.class, false));
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
    public NewTopic emailRetryTopic() {
        return buildTopic(emailRetryTopic, 4, 1, Collections.emptyMap());
    }

    @Bean
    public NewTopic emailDLQTopic() {
        return buildTopic("elevate.emails.dlq", 1, 1, Collections.emptyMap());
    }


    @Bean
    public NewTopic deadLetterTopic() {
        return buildTopic("elevate.transactions.dlq", 1, 1, Collections.emptyMap());
    }

    @Bean
    public NewTopic emailTopic() {
        return buildTopic(emailTopic, 4, 1, Collections.emptyMap());
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


    @PostConstruct
    public void checkKafkaTopics() {
        try {
            AdminClient adminClient = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
            ListTopicsResult topics = adminClient.listTopics();
            Set<String> existingTopics = topics.names().get();

            List<String> requiredTopics = Arrays.asList(
                    "elevate.transactions",
                    "elevate.transactions.retry",
                    "elevate.transactions.dlq",
                    "elevate.emails",
                    "elevate.emails.retry",
                    "elevate.emails.dlq",
                    "elevate.notifications",
                    "elevate.notifications.retry",
                    "elevate.notifications.dlq"
            );
            for (String topic : requiredTopics) {
                if (!existingTopics.contains(topic)) {
                    log.error("Required Kafka topic {} does not exist", topic);
                    createTopic(adminClient, topic);
                }
            }
        } catch (Exception e) {
            log.error("Error checking Kafka topics", e);
        }
    }

    private void createTopic(AdminClient adminClient, String topicName) {
        try {
            NewTopic topic = new NewTopic(topicName, 3, (short) 1);
            adminClient.createTopics(Collections.singleton(topic)).all().get();
            log.info("Created Kafka topic {}", topicName);
        } catch (Exception e) {
            log.error("Error creating Kafka topic {}", topicName, e);
        }
    }


    @Bean
    public KafkaTemplate<String, Object> genericKafkaTemplate() {
        return new KafkaTemplate<>(genericProducerFactory());
    }

    private ProducerFactory<String, Object> genericProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

}
