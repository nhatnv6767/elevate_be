package com.elevatebanking.config;

import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DockerConfig {

    private static final List<String> REQUIRED_SERVICES = Arrays.asList(
            "postgres", "redis", "zookeeper", "kafka"
    );
    @Value("${docker.host:tcp://192.168.1.128:2375}")
    private String dockerHost;
    private DockerClient dockerClient;

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    @PostConstruct
    public void init() throws Exception {
        initializeDockerClient();
        // Sau khi khởi tạo Docker client, bắt đầu khởi tạo services
        initializeDockerServices();
    }

    private void initializeDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(false)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    @Bean
    public DockerClient dockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(false)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    public void initializeDockerServices() throws Exception {
        log.info("Initializing Docker services...");

        try {

            for (String service : REQUIRED_SERVICES) {
                String containerName = "elevate-banking-" + service;
                if (!isContainerRunningAndHealthy(containerName)) {
                    createAndStartContainer(service);
                }
            }

            // Xóa container cũ nếu tồn tại
            List<Container> existingContainers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton("elevate-banking-postgres"))
                    .withShowAll(true)
                    .exec();

            for (Container container : existingContainers) {
                log.info("Removing existing container: {}", container.getId());
                dockerClient.removeContainerCmd(container.getId())
                        .withForce(true)
                        .exec();
            }

            // Pull image
            dockerClient.pullImageCmd("postgres:latest")
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            // Create container
            CreateContainerResponse container = dockerClient.createContainerCmd("postgres:latest")
                    .withName("elevate-banking-postgres")
                    .withEnv(
                            "POSTGRES_DB=elevate_banking",
                            "POSTGRES_USER=root",
                            "POSTGRES_PASSWORD=123456",
                            "POSTGRES_HOST_AUTH_METHOD=trust",
                            "POSTGRES_INITDB_ARGS=--auth-host=trust"
                    )
                    .withHostConfig(HostConfig.newHostConfig()
                            .withPortBindings(PortBinding.parse("5432:5432"))
                            .withPublishAllPorts(true))
                    .withExposedPorts(ExposedPort.tcp(5432))
                    .exec();

            // Start container
            dockerClient.startContainerCmd(container.getId()).exec();

            // Wait for PostgreSQL to be ready
            if (!waitForPostgresqlContainer()) {
                throw new RuntimeException("PostgreSQL failed to start");
            }

            log.info("PostgreSQL container is ready!");

        } catch (Exception e) {
            log.error("Failed to initialize Docker services", e);
            throw e;
        }
    }

    private boolean waitForPostgresqlContainer() {
        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                attempt++;
                log.info("Attempt {} to connect to PostgreSQL", attempt);

                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("192.168.1.128", 5432), 1000);
                    Thread.sleep(5000);  // Đợi thêm 5s sau khi port đã mở

                    // Test database connection
                    try (Connection conn = DriverManager.getConnection(
                            "jdbc:postgresql://192.168.1.128:5432/elevate_banking",
                            "root", "123456")) {
                        if (conn.isValid(5)) {
                            log.info("Successfully connected to PostgreSQL");
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to connect: {}", e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    @PreDestroy
    public void cleanup() {
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .withDockerTlsVerify(false)
                    .build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);

            // Remove container
            List<Container> containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton("elevate-banking-postgres"))
                    .withShowAll(true)
                    .exec();
            if (!containers.isEmpty()) {
                dockerClient.removeContainerCmd(containers.get(0).getId())
                        .withForce(true)
                        .exec();
            }

            // Remove network
            List<Network> networks = dockerClient.listNetworksCmd()
                    .withNameFilter("elevate-banking-network")
                    .exec();
            if (!networks.isEmpty()) {
                dockerClient.removeNetworkCmd(networks.get(0).getId()).exec();
            }
        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
    }


    private boolean isContainerRunningAndHealthy(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withShowAll(true)
                    .exec();

            if (!containers.isEmpty()) {
                Container container = containers.get(0);
                return "running".equalsIgnoreCase(container.getState());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void createAndStartContainer(String service) {
        String containerName = "elevate-banking-" + service;

        try {
            // Pull image if needed
            dockerClient.pullImageCmd(getImageName(service))
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            // Create container with persistent volume
            CreateContainerResponse container = dockerClient.createContainerCmd(getImageName(service))
                    .withName(containerName)
                    .withHostConfig(createHostConfig(service))
                    .withEnv(getEnvironmentVariables(service))
                    .exec();

            // Start container
            dockerClient.startContainerCmd(container.getId()).exec();

            // Wait for service to be ready
            waitForServiceToBeReady(service);
        } catch (Exception e) {
            log.error("Failed to initialize " + service, e);
            throw new RuntimeException("Failed to initialize " + service, e);
        }
    }

    private HostConfig createHostConfig(String service) {
        return HostConfig.newHostConfig()
                .withPortBindings(getPortBindings(service))
                .withBinds(createBinds(service))
                .withNetworkMode("elevate-banking-network");
    }

    private List<Bind> createBinds(String service) {
        String volumeName = String.format("elevate-banking_%s_data", service);
        switch (service) {
            case "postgres":
                return Collections.singletonList(
                        new Bind(volumeName, new Volume("/var/lib/postgresql/data"))
                );
            case "redis":
                return Collections.singletonList(
                        new Bind(volumeName, new Volume("/data"))
                );
            case "zookeeper":
                return Collections.singletonList(
                        new Bind(volumeName, new Volume("/var/lib/zookeeper/data"))
                );
            case "kafka":
                return Collections.singletonList(
                        new Bind(volumeName, new Volume("/var/lib/kafka/data"))
                );
            default:
                return Collections.emptyList();
        }
    }

    private List<String> getEnvironmentVariables(String service) {
        switch (service) {
            case "postgres":
                return Arrays.asList(
                        "POSTGRES_DB=elevate_banking",
                        "POSTGRES_USER=root",
                        "POSTGRES_PASSWORD=123456"
                );
            // Add configs for other services
            default:
                return Collections.emptyList();
        }
    }

    private void waitForServiceToBeReady(String service) {
        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                switch (service) {
                    case "postgres":
                        checkPostgresConnection();
                        break;
                    case "redis":
                        checkRedisConnection();
                        break;
                    case "zookeeper":
                        checkZookeeperConnection();
                        break;
                    case "kafka":
                        checkKafkaConnection();
                        break;
                }
                return;
            } catch (Exception e) {
                attempt++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Service check interrupted", ie);
                }
            }
        }
        throw new RuntimeException(service + " failed to start after " + maxAttempts + " attempts");
    }

    private String getImageName(String service) {
        switch (service) {
            case "postgres":
                return "postgres:latest";
            case "redis":
                return "redis:latest";
            case "zookeeper":
                return "confluentinc/cp-zookeeper:latest";
            case "kafka":
                return "confluentinc/cp-kafka:latest";
            default:
                throw new IllegalArgumentException("Unknown service: " + service);
        }
    }

    private PortBinding[] getPortBindings(String service) {
        switch (service) {
            case "postgres":
                return new PortBinding[]{PortBinding.parse("5432:5432")};
            case "redis":
                return new PortBinding[]{PortBinding.parse("6379:6379")};
            case "zookeeper":
                return new PortBinding[]{PortBinding.parse("2181:2181")};
            case "kafka":
                return new PortBinding[]{
                        PortBinding.parse("9092:9092"),
                        PortBinding.parse("29092:29092")
                };
            default:
                return new PortBinding[]{};
        }
    }

    private void checkPostgresConnection() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("192.168.1.128", 5432), 1000);

            try (Connection conn = DriverManager.getConnection(
                    "jdbc:postgresql://192.168.1.128:5432/elevate_banking",
                    "root",
                    "123456")) {
                if (conn.isValid(5)) {
                    log.info("Successfully connected to PostgreSQL");
                    return;
                }
            }
            throw new RuntimeException("Failed to connect to PostgreSQL");
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to PostgreSQL", e);
        }
    }

    private void checkRedisConnection() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName("192.168.1.128");
        redisConfig.setPort(6379);

        try {
            LettuceConnectionFactory redisConnectionFactory = new LettuceConnectionFactory(redisConfig);
            redisConnectionFactory.afterPropertiesSet();
            RedisConnection connection = redisConnectionFactory.getConnection();
            if (connection.ping() != null) {
                log.info("Successfully connected to Redis");
                return;
            }
            throw new RuntimeException("Failed to connect to Redis");
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Redis", e);
        }
    }

    private void checkZookeeperConnection() {
        try {
            CuratorFramework client = CuratorFrameworkFactory.newClient(
                    "192.168.1.128:2181",
                    new ExponentialBackoffRetry(1000, 3));
            client.start();
            if (client.blockUntilConnected(5, TimeUnit.SECONDS)) {
                log.info("Successfully connected to Zookeeper");
                client.close();
                return;
            }
            throw new RuntimeException("Failed to connect to Zookeeper");
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Zookeeper", e);
        }
    }

    private void checkKafkaConnection() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.1.128:29092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.partitionsFor("__consumer_offsets"); // Check if Kafka is accessible
            log.info("Successfully connected to Kafka");
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Kafka", e);
        }
    }
}