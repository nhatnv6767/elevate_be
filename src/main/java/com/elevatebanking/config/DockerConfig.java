package com.elevatebanking.config;

import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.CreateContainerCmdImpl;
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
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.net.Socket;
import java.net.InetSocketAddress;
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
        initializeDockerNetwork();

        log.info("Initializing Docker services...");


        try {

            for (String service : REQUIRED_SERVICES) {
                String containerName = "elevate-banking-" + service;
                if (!isContainerRunning(containerName)) {
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

    private void createAndStartContainer(String service) {
        String containerName = "elevate-banking-" + service;

        try {
            // Pull image if needed
            dockerClient.pullImageCmd(getImageName(service))
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            // Remove existing container if any
            dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withShowAll(true)
                    .exec()
                    .forEach(container -> {
                        try {
                            dockerClient.removeContainerCmd(container.getId())
                                    .withForce(true)
                                    .exec();
                            log.info("Removed existing container: {}", containerName);
                            Thread.sleep(2000);
                        } catch (Exception e) {
                            log.warn("Failed to remove container: {}", e.getMessage());
                        }
                    });
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

    private CreateContainerResponse createKafkaContainer(String service) {
        String containerName = "elevate-banking-" + service;
        String networkName = "elevate-banking-network";

        // Tạo host config
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode(networkName)
                .withPortBindings(
                        PortBinding.parse("9092:9092"),
                        PortBinding.parse("29092:29092")
                )
                .withLinks(new Link("elevate-banking-zookeeper", "zookeeper"));

        // Tạo container với network aliases thông qua environment variables
        List<String> env = getEnvironmentVariables(service);
        env.add("KAFKA_ADVERTISED_HOST_NAME=kafka");

        CreateContainerResponse container = dockerClient.createContainerCmd(getImageName(service))
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withEnv(env)
                .exec();

        // Kết nối container với network
        try {
            dockerClient.connectToNetworkCmd()
                    .withNetworkId(networkName)
                    .withContainerId(container.getId())
                    .exec();

            // Start container sau khi kết nối network
            dockerClient.startContainerCmd(container.getId()).exec();

        } catch (Exception e) {
            log.error("Failed to setup Kafka container", e);
            // Cleanup nếu có lỗi
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
            throw new RuntimeException("Failed to setup Kafka container", e);
        }

        return container;
    }

    private HostConfig createHostConfig(String service) {
        HostConfig config = HostConfig.newHostConfig()
                .withNetworkMode("elevate-banking-network");

        switch (service) {
            case "kafka":
                return HostConfig.newHostConfig()
                        .withNetworkMode("elevate-banking-network")
                        .withPortBindings(Arrays.asList(
                                PortBinding.parse("9092:9092"),
                                PortBinding.parse("29092:29092")
                        ))
                        // Thêm DNS aliases
                        .withNetworkMode(String.valueOf(Arrays.asList("kafka")))
                        .withLinks(new Link("elevate-banking-zookeeper", "zookeeper"));
            // ... other cases ...
        }
        return config;
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
            case "zookeeper":
                return Arrays.asList(
                        "ZOOKEEPER_CLIENT_PORT=2181",
                        "ZOOKEEPER_TICK_TIME=2000",
                        "ALLOW_ANONYMOUS_LOGIN=yes"
                );
            case "kafka":
                return Arrays.asList(
                        "KAFKA_BROKER_ID=1",
                        // Sử dụng internal IP thay vì hostname
                        "KAFKA_LISTENERS=INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092",
                        "KAFKA_ADVERTISED_LISTENERS=INTERNAL://172.18.0.4:9092,EXTERNAL://192.168.1.128:29092",
                        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT",
                        "KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL",
                        // Sử dụng container name với network alias
                        "KAFKA_ZOOKEEPER_CONNECT=elevate-banking-zookeeper:2181",
                        "KAFKA_BROKER_ID=1",
                        "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1",
                        "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1",
                        "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1",
                        // Thêm timeout dài hơn cho khởi động
                        "KAFKA_ZOOKEEPER_CONNECTION_TIMEOUT_MS=60000",
                        "KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS=60000"
                );
            case "redis":
                return Arrays.asList(
                        // Cấu hình cơ bản
                        "REDIS_PORT=6379",
                        "REDIS_BIND_ADDRESS=0.0.0.0",

                        // Cấu hình bảo mật
                        "REDIS_PASSWORD=123456", // Thay thế bằng mật khẩu thực
                        "REDIS_ALLOW_EMPTY_PASSWORD=no",

                        // Cấu hình hiệu suất
                        "REDIS_MAXMEMORY=2gb",
                        "REDIS_MAXMEMORY_POLICY=allkeys-lru",

                        // Cấu hình persistence
                        "REDIS_SAVE_TO_DISK=yes",
                        "REDIS_AOF_ENABLED=yes",

                        // Cấu hình timeout
                        "REDIS_TIMEOUT=300",

                        // Cấu hình giới hạn kết nối
                        "REDIS_MAXCLIENTS=10000",

                        // Cấu hình TLS/SSL (nếu cần)
                        "REDIS_TLS_PORT=6380",
                        "REDIS_TLS_ENABLED=no"
                );
            default:
                return Collections.emptyList();
        }
    }

    private void waitForServiceToBeReady(String service) {
        int maxRetries = 30;
        int retryDelay = 2000;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                switch (service) {
                    case "postgres":
                        checkPostgresConnection();
                        break;
                    case "redis":
                        checkRedisConnection();
                        break;
                    case "zookeeper":
                        if (checkZookeeperConnection()) {
                            log.info("Successfully connected to Zookeeper");
                            return;
                        }
                        break;
                    case "kafka":
                        if (attempt == 0) {
                            Thread.sleep(10000); // Đợi 10s cho Zookeeper khởi động hoàn toàn
                        }
                        checkKafkaConnection();
                        break;
                }
                attempt++;
                if (attempt < maxRetries) {
                    log.info("Retrying {} connection in {} ms...", service, retryDelay);
                    Thread.sleep(retryDelay);
                }
                return;
            } catch (Exception e) {
                attempt++;
                log.warn("Service {} check failed (attempt {}/{}): {}",
                        service, attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new RuntimeException(service + " failed to start after " + maxRetries + " attempts");
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
        redisConfig.setPassword("123456");

        try {
            LettuceConnectionFactory redisConnectionFactory = new LettuceConnectionFactory(redisConfig);
            redisConnectionFactory.afterPropertiesSet();
            RedisConnection connection = redisConnectionFactory.getConnection();
            if (connection.ping() != null) {
                log.info("Successfully connected to Redis");
                connection.close();
                redisConnectionFactory.destroy();
                return;
            }
            throw new RuntimeException("Failed to connect to Redis");
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Redis", e);
        }
    }

    private boolean checkZookeeperConnection() {
        int maxRetries = 5;
        int retryDelay = 1000; // in milliseconds

        for (int retry = 1; retry <= maxRetries; retry++) {
            try {
                CuratorFramework client = CuratorFrameworkFactory.newClient(
                        "192.168.1.128:2181",
                        new ExponentialBackoffRetry(1000, 3));

                client.start();

                if (client.blockUntilConnected(5, TimeUnit.SECONDS)) {
                    log.info("Successfully connected to Zookeeper");
                    client.close();
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to connect to Zookeeper (attempt {} of {}): {}", retry, maxRetries, e.getMessage());
            }

            if (retry < maxRetries) {
                log.info("Retrying Zookeeper connection in {} ms...", retryDelay);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry interrupted", e);
                    return false;
                }
            } else {
                log.error("Failed to connect to Zookeeper after {} attempts", maxRetries);
                return false;
            }
        }

        return false;
    }

    private void checkKafkaConnection() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.1.128:29092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");

        int maxRetries = 5;
        int retryCount = 0;
        Exception lastException = null;
        while (retryCount < maxRetries) {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                // Kiểm tra kết nối bằng cách lấy cluster metadata
                producer.partitionsFor("_kafka_healthcheck");
                log.info("Successfully connected to Kafka");
                return;
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("Failed to connect to Kafka (attempt {}/{}): {}",
                        retryCount, maxRetries, e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Kafka connection check interrupted", ie);
                }
            }
        }

        throw new RuntimeException("Failed to connect to Kafka after " + maxRetries +
                " attempts. Last error: " + lastException.getMessage(), lastException);
    }

    private boolean isContainerRunning(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withShowAll(true)
                    .exec();
            return !containers.isEmpty() && "running".equalsIgnoreCase(containers.get(0).getState());
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeDockerNetwork() {
        try {
            List<Network> networks = dockerClient.listNetworksCmd()
                    .withNameFilter("elevate-banking-network")
                    .exec();
            if (networks.isEmpty()) {
                dockerClient.createNetworkCmd()
                        .withName("elevate-banking-network")
                        .withDriver("bridge")
                        .exec();
                log.info("Created network: elevate-banking-network");
            } else {
                log.info("Network elevate-banking-network already exists");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Docker network", e);
            throw new RuntimeException("Failed to initialize Docker network", e);
        }
    }
}
