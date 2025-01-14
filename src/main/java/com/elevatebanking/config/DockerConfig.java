package com.elevatebanking.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.exception.NotFoundException;
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
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.io.IOException;
import java.sql.SQLException;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
@DependsOn("entityManagerFactory")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DockerConfig {


    private static final String POSTGRES_VOLUME = "elevate-banking-postgres-data";
    private static final String KAFKA_VOLUME = "elevate-banking-kafka-data";
    private static final String KAFKA_SECRETS_VOLUME = "elevate-banking-kafka-secrets";
    private static final String REDIS_VOLUME = "elevate-banking-redis-data";
    private static final String ZOOKEEPER_VOLUME = "elevate-banking-zookeeper-data";
    private static final String ZOOKEEPER_LOG_VOLUME = "elevate-banking-zookeeper-log";
    private static final String ZOOKEEPER_SECRETS_VOLUME = "elevate-banking-zookeeper-secrets";
    private static final String CASSANDRA_VOLUME = "elevate-banking-cassandra-data";

    private static final String CASSANDRA_CONTAINER = "elevate-banking-cassandra";
    private static final String POSTGRES_CONTAINER = "elevate-banking-postgres";
    private static final String KAFKA_CONTAINER = "elevate-banking-kafka";
    private static final String REDIS_CONTAINER = "elevate-banking-redis";
    private static final String ZOOKEEPER_CONTAINER = "elevate-banking-zookeeper";

    private static final String SERVER_HOST = "192.168.1.128";

    private static final String NETWORK_NAME = "elevate-banking-network";
    private static final List<String> PROTECTED_VOLUMES = Arrays.asList(
            "portainer_data",
            "portainer-data"
    );

    @Value("${docker.host:tcp://192.168.1.128:2375}")
    private String dockerHost;
    private DockerClient dockerClient;

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    @Bean
    public DockerClient dockerClient() {
        return this.dockerClient;
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    @PostConstruct
    public void init() throws Exception {
        initializeDockerClient();
        if (!"none".equals(ddlAuto)) {
            log.info("Waiting for Docker services initialization before schema creation...");

            if (areAllServicesRunning()) {
                log.info("All services are already running, skipping initialization");
                return;
            }

            initializeDockerNetwork();
            initializeDockerServices();
        } else {
            log.info("Docker services initialization skipped");
        }
    }

    @Bean
    public CommandLineRunner cleanupDockerResources(DockerClient dockerClient) {
        return args -> {
            // Cleanup orphaned volumes
            List<InspectVolumeResponse> volumes = dockerClient.listVolumesCmd().exec().getVolumes();
            for (InspectVolumeResponse volume : volumes) {
                String volumeName = volume.getName();
                // Only keep volumes with our specific prefix
                if (!volumeName.startsWith("elevate-banking-")) {
                    try {
                        dockerClient.removeVolumeCmd(volumeName).exec();
                        log.info("Removed orphaned volume: {}", volumeName);
                    } catch (Exception e) {
                        log.warn("Could not remove volume {}: {}", volumeName, e.getMessage());
                    }
                }
            }

            // Add wait for database
            int maxRetries = 30;
            int retryCount = 0;
            boolean connected = false;

            while (!connected && retryCount < maxRetries) {
                try (Connection conn = DriverManager.getConnection(
                        "jdbc:postgresql://192.168.1.128:5432/elevate_banking",
                        "root", "123456")) {
                    if (conn.isValid(5)) {
                        connected = true;
                        log.info("Successfully connected to database");
                    }
                } catch (Exception e) {
                    retryCount++;
                    log.info("Waiting for database to be ready... Attempt {}/{}", retryCount, maxRetries);
                    Thread.sleep(2000);
                }
            }

            if (!connected) {
                throw new RuntimeException("Could not connect to database after " + maxRetries + " attempts");
            }
        };
    }

    private boolean areAllServicesRunning() {
        try {
            return isServiceRunningAndAccessible(POSTGRES_CONTAINER, 5432) &&
                    isServiceRunningAndAccessible(REDIS_CONTAINER, 6379) &&
                    isServiceRunningAndAccessible(ZOOKEEPER_CONTAINER, 2181) &&
                    isServiceRunningAndAccessible(KAFKA_CONTAINER, 9092) &&
                    isServiceRunningAndAccessible(CASSANDRA_CONTAINER, 9042);
        } catch (Exception e) {
            log.warn("Failed to check if all services are running - {}", e.getMessage());
            return false;
        }
    }

    private boolean isServiceRunningAndAccessible(String containerName, int port) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withStatusFilter(Collections.singleton("running"))
                    .exec();

            if (containers.isEmpty()) {
                return false;
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(SERVER_HOST, port), 1000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeDockerNetwork() {
        try {
            // Kiểm tra network đã tồn tại chưa
            List<Network> networks = dockerClient.listNetworksCmd()
                    .withFilter("name", Arrays.asList(NETWORK_NAME))
                    .exec();

            if (networks.isEmpty()) {
                // Tạo network mới nếu chưa tồn tại
                dockerClient.createNetworkCmd()
                        .withName(NETWORK_NAME)
                        .withDriver("bridge")
                        .exec();
                log.info("Created Docker network: {}", NETWORK_NAME);
            } else {
                log.info("Docker network {} already exists", NETWORK_NAME);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Docker network", e);
            throw new RuntimeException("Failed to initialize Docker network", e);
        }
    }

    private HostConfig createHostConfig(String service) {
        switch (service) {
            case "postgres":
                return HostConfig.newHostConfig()
                        .withNetworkMode(NETWORK_NAME)
                        .withPortBindings(PortBinding.parse("5432:5432"))
                        .withBinds(new Bind(POSTGRES_VOLUME, new Volume("/var/lib/postgresql/data")));
            case "redis":
                return HostConfig.newHostConfig()
                        .withNetworkMode(NETWORK_NAME)
                        .withPortBindings(PortBinding.parse("6379:6379"))
                        .withBinds(new Bind(REDIS_VOLUME, new Volume("/data")));
            case "zookeeper":
                return HostConfig.newHostConfig()
                        .withNetworkMode(NETWORK_NAME)
                        .withPortBindings(PortBinding.parse("2181:2181"))
                        .withBinds(
                                new Bind(ZOOKEEPER_VOLUME, new Volume("/var/lib/zookeeper/data")),
                                new Bind(ZOOKEEPER_LOG_VOLUME, new Volume("/var/lib/zookeeper/log")),
                                new Bind(ZOOKEEPER_SECRETS_VOLUME, new Volume("/etc/zookeeper/secrets")));
            // .withBinds(new Bind(ZOOKEEPER_VOLUME, new Volume("/var/lib/zookeeper")));

            case "kafka":
                return HostConfig.newHostConfig()
                        .withNetworkMode(NETWORK_NAME)
                        .withPortBindings(
                                PortBinding.parse("9092:9092"))
                        .withBinds(new Bind(KAFKA_VOLUME, new Volume("/var/lib/kafka/data")),
                                new Bind(KAFKA_SECRETS_VOLUME, new Volume("/etc/kafka/secrets")))
                        .withLinks(new Link("elevate-banking-zookeeper", "zookeeper"));

            case "cassandra":
                return HostConfig.newHostConfig()
                        .withNetworkMode(NETWORK_NAME)
                        .withPortBindings(
                                PortBinding.parse("9042:9042"),   // CQL native
                                PortBinding.parse("7000:7000"),   // Internode
                                PortBinding.parse("7001:7001"),   // TLS Internode
                                PortBinding.parse("7199:7199")    // JMX
                        )
                        .withBinds(new Bind(CASSANDRA_VOLUME, new Volume("/var/lib/cassandra")))
                        .withExtraHosts("cassandra:192.168.1.128")
                        .withPublishAllPorts(true);


            default:
                return HostConfig.newHostConfig()
                        .withNetworkMode(NETWORK_NAME);
        }
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

    private void handleExistingContainer(String containerName, String serviceType, int port) {
        try {
            List<Container> runningContainers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withStatusFilter(Collections.singleton("running"))
                    .exec();

            if (!runningContainers.isEmpty()) {
                log.info("{} container is already running", serviceType);
                if (isPortAccesible(SERVER_HOST, port)) {
                    log.info("{} is ready on port {}", serviceType, port);
                }
                return;
            }

            List<Container> stoppedContainers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withStatusFilter(Collections.singleton("exited"))
                    .exec();

            if (!stoppedContainers.isEmpty()) {
                String containerId = stoppedContainers.get(0).getId();
                log.info("Starting existing {} container", serviceType);
                dockerClient.startContainerCmd(containerId).exec();
            } else {
                log.info("Creating and starting {} container", serviceType);
                createAndStartContainer(serviceType);
            }

            // Wait for container to be ready
            waitForServiceToBeReady(serviceType);

        } catch (Exception e) {
            log.error("Error handling {} container: {}", serviceType, e.getMessage());
            throw new RuntimeException("Failed to handle " + serviceType + " container", e);
        }
    }

    private boolean isPortAccesible(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForPort(int port) {
        int maxAttempts = 10;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(SERVER_HOST, port), 1000);
                log.info("Port {} is available", port);
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new RuntimeException("Port " + port + " not available after " + maxAttempts + " attempts");
    }


    public void initializeDockerServices() throws Exception {
        log.info("Initializing Docker services...");

        try {
            cleanupOrphanedVolumes();
            initializeVolumes();

            handleExistingContainer(CASSANDRA_CONTAINER, "cassandra", 9042);
            waitForServiceToBeReady("cassandra");
            Thread.sleep(10000);

            // 1. Kiểm tra và khởi động PostgreSQL
            handleExistingContainer(POSTGRES_CONTAINER, "postgres", 5432);
            log.info("PostgreSQL is ready");
            Thread.sleep(2000);

            // 2. Kiểm tra và khởi động Redis
            handleExistingContainer(REDIS_CONTAINER, "redis", 6379);
            waitForServiceToBeReady("redis");
            log.info("Redis is ready");

            // 3. Kiểm tra và khởi động Zookeeper
            handleExistingContainer(ZOOKEEPER_CONTAINER, "zookeeper", 2181);
            waitForServiceToBeReady("zookeeper");
            Thread.sleep(1000);
            log.info("Zookeeper is ready");

            // 4. Kiểm tra và khởi động Kafka
            handleExistingContainer(KAFKA_CONTAINER, "kafka", 9092);
            Thread.sleep(1000);
            waitForServiceToBeReady("kafka");
            log.info("Kafka is ready");
            Thread.sleep(2000);


        } catch (Exception e) {
            log.error("Failed to initialize Docker services", e);
            throw e;
        }
    }

    private void initializeVolumes() {
        List<String> volumes = Arrays.asList(
                POSTGRES_VOLUME, REDIS_VOLUME, KAFKA_VOLUME,
                KAFKA_SECRETS_VOLUME, ZOOKEEPER_VOLUME, ZOOKEEPER_LOG_VOLUME,
                ZOOKEEPER_SECRETS_VOLUME, CASSANDRA_VOLUME);
        volumes.forEach(this::createVolumeIfNotExists);
    }

    @PreDestroy
    public void cleanup() {
        // neu len moi truong product thi mo ra đe dung cac container
        // try {
        // // Chỉ dừng các container nếu cần, không xóa
        // List<Container> containers = dockerClient.listContainersCmd()
        // .withNameFilter(Collections.singleton("elevate-banking"))
        // .withShowAll(true)
        // .exec();
        //
        // for (Container container : containers) {
        // if ("running".equalsIgnoreCase(container.getState())) {
        // log.info("Stopping container: {}", container.getNames()[0]);
        // dockerClient.stopContainerCmd(container.getId()).exec();
        // }
        // }
        // } catch (Exception e) {
        // log.error("Error during cleanup", e);
        // }
    }

    private void createAndStartContainer(String service) {
        String containerName = "elevate-banking-" + service;
        try {
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
                            Thread.sleep(2000); // Wait for container cleanup
                        } catch (Exception e) {
                            log.warn("Failed to remove container: {}", e.getMessage());
                        }
                    });

            // Pull image if needed
            dockerClient.pullImageCmd(getImageName(service))
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            // Create container
            CreateContainerResponse container = dockerClient.createContainerCmd(getImageName(service))
                    .withName(containerName)
                    .withHostConfig(createHostConfig(service))
                    .withEnv(getEnvironmentVariables(service))
                    .exec();

            // Start container
            dockerClient.startContainerCmd(container.getId()).exec();

            // Wait for container to be ready
            waitForServiceToBeReady(service);
            log.info("Successfully started {} container", service);
        } catch (Exception e) {
            log.error("Failed to initialize {}", service, e);
            throw new RuntimeException("Failed to initialize " + service, e);
        }
    }

    private CreateContainerResponse createKafkaContainer(String service) {
        String containerName = "elevate-banking-" + service;
        String networkName = "elevate-banking-network";

        // Create host config
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode(networkName)
                .withPortBindings(
                        PortBinding.parse("9092:9092"))
                .withLinks(new Link("elevate-banking-zookeeper", "zookeeper"));

        // Create container with network aliases through environment variables
        List<String> env = getEnvironmentVariables(service);
        env.add("KAFKA_ADVERTISED_HOST_NAME=kafka");

        CreateContainerResponse container = dockerClient.createContainerCmd(getImageName(service))
                .withName(containerName)
                .withHostConfig(hostConfig)
                .withEnv(env)
                .exec();

        // Connect container to network
        try {
            dockerClient.connectToNetworkCmd()
                    .withNetworkId(networkName)
                    .withContainerId(container.getId())
                    .exec();

            // Start container after connecting to network
            dockerClient.startContainerCmd(container.getId()).exec();

        } catch (Exception e) {
            log.error("Failed to setup Kafka container", e);
            // Cleanup if error occurs
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
            throw new RuntimeException("Failed to setup Kafka container", e);
        }

        return container;
    }

    private List<String> getEnvironmentVariables(String service) {
        switch (service) {
            case "postgres":
                return Arrays.asList(
                        "POSTGRES_DB=elevate_banking",
                        "POSTGRES_USER=root",
                        "POSTGRES_PASSWORD=123456",
                        "POSTGRES_HOST_AUTH_METHOD=trust",
                        "POSTGRES_LISTEN_ADDRESSES=*");
            case "zookeeper":
                return Arrays.asList(
                        "ZOOKEEPER_CLIENT_PORT=2181",
                        "ZOOKEEPER_TICK_TIME=2000"
                        // "ALLOW_ANONYMOUS_LOGIN=yes"
                );
            case "kafkaBACK":
                return Arrays.asList(
                        "KAFKA_BROKER_ID=1",
                        "KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181",
                        "KAFKA_LISTENERS=INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:9092",
                        "KAFKA_ADVERTISED_LISTENERS=INTERNAL://0.0.0.0:9092,PLAINTEXT_HOST://192.168.1.128:9092",
                        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT",
                        "KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT",
                        "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1");
            case "kafka":
                return Arrays.asList(
                        "KAFKA_BROKER_ID=1",
                        "KAFKA_ZOOKEEPER_CONNECT=elevate-banking-zookeeper:2181",
                        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT",
                        "KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092",
                        "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://elevate-banking-kafka:29092,EXTERNAL://192.168.1.128:9092",
                        "KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT",
                        "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1",
                        "KAFKA_AUTO_CREATE_TOPICS_ENABLE=true",
                        "KAFKA_NUM_PARTITIONS=1",
                        "KAFKA_DEFAULT_REPLICATION_FACTOR=1");
            case "redis":
                return Arrays.asList(
                        "REDIS_PASSWORD=",
                        "ALLOW_EMPTY_PASSWORD=yes",
                        "REDIS_BIND=0.0.0.0");
            case "cassandra":
                return Arrays.asList(
                        "CASSANDRA_CLUSTER_NAME=elevate_banking",
                        "CASSANDRA_DC=datacenter1",
                        "CASSANDRA_RACK=rack1",
                        "CASSANDRA_ENDPOINT_SNITCH=SimpleSnitch",
                        // Để Cassandra bind vào tất cả các interface trong container
                        "CASSANDRA_LISTEN_ADDRESS=0.0.0.0",
                        // Địa chỉ broadcast là địa chỉ thật của máy host
                        "CASSANDRA_BROADCAST_ADDRESS=192.168.1.128",
                        "CASSANDRA_START_RPC=true",
                        // RPC address cũng bind vào tất cả các interface
                        "CASSANDRA_RPC_ADDRESS=0.0.0.0",
                        "CASSANDRA_BROADCAST_RPC_ADDRESS=192.168.1.128",
                        "CASSANDRA_SEEDS=192.168.1.128",
                        // Cấu hình authentication
                        "CASSANDRA_AUTHENTICATOR=PasswordAuthenticator",
                        "CASSANDRA_AUTHORIZER=CassandraAuthorizer",
                        // Credentials
                        "CASSANDRA_USER=root",
                        "CASSANDRA_PASSWORD=123456"
                );
            case "redisBACK":
                return Arrays.asList(
                        // Basic configuration
                        "REDIS_PORT=6379",
                        "REDIS_BIND_ADDRESS=0.0.0.0",

                        // Security configuration
                        "REDIS_PASSWORD=123456", // Replace with actual password
                        "REDIS_ALLOW_EMPTY_PASSWORD=no",

                        // Performance configuration
                        "REDIS_MAXMEMORY=2gb",
                        "REDIS_MAXMEMORY_POLICY=allkeys-lru",

                        // Persistence configuration
                        "REDIS_SAVE_TO_DISK=yes",
                        "REDIS_AOF_ENABLED=yes",

                        // Timeout configuration
                        "REDIS_TIMEOUT=300",

                        // Connection limit configuration
                        "REDIS_MAXCLIENTS=10000",

                        // TLS/SSL configuration (if needed)
                        "REDIS_TLS_PORT=6380",
                        "REDIS_TLS_ENABLED=no");
            default:
                return Collections.emptyList();
        }
    }

    private void waitForServiceToBeReady(String service) {
        int maxRetries = service.equals("cassandra") ? 60 : 5;
        int retryDelay = service.equals("cassandra") ? 5000 : 500;
        int attempt = 0;
        String host = service.equals("cassandra") ? "127.0.0.1" : SERVER_HOST; // Use 127.0.0.1 for Cassandra

        while (attempt < maxRetries) {
            try {
                switch (service) {
                    case "postgres":
                        if (isPortAccesible(host, 5432)) {
                            checkPostgresConnection();
                            return;
                        }
                        break;
                    case "redis":
                        if (isPortAccesible(host, 6379)) {
                            checkRedisConnection();
                            return;
                        }
                        break;
                    case "zookeeper":
                        if (checkZookeeperConnection() && isPortAccesible(host, 2181)) {
                            log.info("Successfully connected to Zookeeper");
                            return;
                        }
                        break;
                    case "kafka":
                        waitForPort(9092);
                        if (isPortAccesible(host, 9092)) {
                            checkKafkaConnection();
                            return;
                        }
                        break;
                    case "cassandra":
                        if (isPortAccesible(host, 9042)) {
                            try {
                                checkCassandraConnection(host);
                                log.info("Successfully connected to Cassandra");
                                return;
                            } catch (Exception e) {
                                log.error("Failed to connect to Cassandra: {}", e.getMessage(), e);
//                                throw e;
                            }
                        }
                        break;
                }
                attempt++;
                if (attempt < maxRetries) {
                    log.info("Retrying {} connection in {} ms...", service, retryDelay);
                    Thread.sleep(retryDelay);
                }
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
            case "cassandra":
                return "cassandra:latest";
            default:
                throw new IllegalArgumentException("Unknown service: " + service);
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

    private void checkCassandraConnection(String host) {
        try (Socket socket = new Socket()) {
            // First check if port is accessible
            socket.connect(new InetSocketAddress(host, 9042), 5000);

            // Use Cassandra driver to check connection
            try (CqlSession session = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress(host, 9042))
                    .withLocalDatacenter("datacenter1")
                    .withAuthCredentials("root", "123456")
//                    .withKeyspace("elevate_banking")
                    .build()) {

                // Execute a simple query to verify connection
                session.execute("CREATE KEYSPACE IF NOT EXISTS elevate_banking " +
                        "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
                ResultSet rs = session.execute("SELECT now() FROM system.local");
                Row row = rs.one();
                if (row != null) {
                    log.info("Successfully connected to Cassandra...");
                    return;
                }
                throw new RuntimeException("Failed to verify Cassandra connection");
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to Cassandra using driver", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to Cassandra port", e);
        }
    }

    private void checkRedisConnection() {
        try {
            // first check if the port is open
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("192.168.1.128", 6379), 1000);
                Thread.sleep(2000);
            }

            // after the port is open, check if we can connect to Redis
            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
            redisConfig.setHostName("192.168.1.128");
            redisConfig.setPort(6379);

            LettuceConnectionFactory redisConnectionFactory = new LettuceConnectionFactory(redisConfig);
            redisConnectionFactory.afterPropertiesSet();

            try {
                RedisConnection connection = redisConnectionFactory.getConnection();
                if (connection.ping() != null) {
                    log.info("Successfully connected to Redis");
                    connection.close();
                    redisConnectionFactory.destroy();
                    return;
                }
            } finally {
                redisConnectionFactory.destroy();
            }

            throw new RuntimeException("Failed to connect to Redis - ping failed");
        } catch (Exception e) {
            log.error("Redis connection check failed", e);
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
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.1.128:9092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "kafka-health-check");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        props.put("security.protocol", "PLAINTEXT");
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "10000");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
        props.put(ProducerConfig.RETRIES_CONFIG, "3");
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;
        while (retryCount < maxRetries) {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
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


    private void createVolumeIfNotExists(String volumeName) {
        try {
            dockerClient.inspectVolumeCmd(volumeName).exec();
            log.info("Volume {} already exists", volumeName);
        } catch (NotFoundException e) {
            dockerClient.createVolumeCmd()
                    .withName(volumeName)
                    .withLabels(Collections.singletonMap("project", "elevate-banking"))
                    .exec();
            log.info("Created volume: {}", volumeName);
        }
    }

    private void cleanupOrphanedVolumes() {
        try {
            // Lấy tất cả volumes
            List<InspectVolumeResponse> volumes = dockerClient.listVolumesCmd().exec().getVolumes();

            // Lấy danh sách container đang chạy
            List<Container> runningContainers = dockerClient.listContainersCmd()
                    .withShowAll(true) // Bao gồm cả stopped containers
                    .exec();

            // Lấy volume IDs đang được sử dụng
            Set<String> usedVolumes = new HashSet<>();
            for (Container container : runningContainers) {
                if (container.getMounts() != null) {
                    container.getMounts().forEach(mount -> {
                        if (mount.getName() != null) {
                            usedVolumes.add(mount.getName());
                        }
                    });
                }
            }

            // Xóa volumes không được sử dụng và không thuộc project
            for (InspectVolumeResponse volume : volumes) {
                String volumeName = volume.getName();
                if (!volumeName.startsWith("elevate-banking-") &&
                        !usedVolumes.contains(volumeName) &&
                        !PROTECTED_VOLUMES.contains(volumeName)
                ) {
                    try {
                        dockerClient.removeVolumeCmd(volumeName).exec();
                        log.info("Removed orphaned volume : {}", volumeName);
                    } catch (Exception e) {
                        log.warn("Couldn't remove volume {} : {}", volumeName, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to cleanup orphaned volumes", e);
        }
    }
}
