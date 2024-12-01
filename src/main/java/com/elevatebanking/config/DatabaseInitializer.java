package com.elevatebanking.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.testcontainers.shaded.com.github.dockerjava.core.DefaultDockerClientConfig;
import org.testcontainers.shaded.com.github.dockerjava.core.DockerClientConfig;
import org.testcontainers.shaded.com.github.dockerjava.core.DockerClientImpl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@DependsOn("dockerConfig")
public class DatabaseInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final DockerClient dockerClient;
    @Value("${docker.host:tcp://192.168.1.128:2375}")
    private String dockerHost;

//    @Autowired
//    public DatabaseInitializer(DockerClient dockerClient) {
//        this.dockerClient = dockerClient;
//    }

    public DatabaseInitializer(@Value("${docker.host:tcp://192.168.1.128:2375}") String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://192.168.1.128:2375")
                .withDockerTlsVerify(false)
                .build();

        // Create HTTP client with specific configurations
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String containerName = "elevate-banking-postgres";
        if (!isContainerRunning(containerName)) {
            initializePostgres();
        } else {
            log.info("PostgreSQL container is already running");
        }
    }

    private boolean isContainerRunning(String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withShowAll(true)
                    .exec();
            return !containers.isEmpty() &&
                    "running".equalsIgnoreCase(containers.get(0).getState());
        } catch (Exception e) {
            return false;
        }
    }

    @PostConstruct
    private void initializePostgres() throws Exception {
        try {

            log.info("Starting PostgreSQL initialization...");

            // Test Docker connection first
            try {
                dockerClient.pingCmd().exec();
                log.info("Docker connection successful");
            } catch (Exception e) {
                log.error("Failed to connect to Docker: {}", e.getMessage());
                throw new RuntimeException("Docker connection failed", e);
            }

            String containerName = "elevate-banking-postgres";

            if (isContainerRunning(containerName)) {
                log.info("PostgreSQL container is already running");
                return;
            }

            List<Container> existingContainers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withShowAll(true)
                    .exec();


            if (existingContainers.isEmpty()) {
                // Pull image
                dockerClient.pullImageCmd("postgres:latest")
                        .exec(new PullImageResultCallback())
                        .awaitCompletion();

                // Create container
                var container = dockerClient.createContainerCmd("postgres:latest")
                        .withName(containerName)
                        .withEnv(
                                "POSTGRES_DB=elevate_banking",
                                "POSTGRES_USER=root", 
                                "POSTGRES_PASSWORD=123456",
                                "LISTEN_ADDRESSES=*"
                        )
                        .withHostConfig(HostConfig.newHostConfig()
                                .withPortBindings(PortBinding.parse("5432:5432"))
                                .withAutoRemove(false)
                                .withBinds(
                                    new Bind("postgres-data-volume", new Volume("/var/lib/postgresql/data"))
                                )
                        )
                        .withExposedPorts(ExposedPort.tcp(5432))
                        .exec();

                // Start container
                dockerClient.startContainerCmd(container.getId()).exec();
            } else {
                // Start existing container if it's stopped
                Container existingContainer = existingContainers.get(0);
                if (!"running".equalsIgnoreCase(existingContainer.getState())) {
                    dockerClient.startContainerCmd(existingContainer.getId()).exec();
                }
            }

            // Wait for PostgreSQL to be ready
            waitForPostgres();
        } catch (Exception e) {
            log.error("Error initializing Postgres", e);
            throw e;
        }
    }

    private void waitForPostgres() throws Exception {
        int attempts = 0;
        int maxAttempts = 120;
        int retryInterval = 5000;
        while (attempts < maxAttempts) {
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(
                        "jdbc:postgresql://192.168.1.128:5432/elevate_banking",
                        "root", "123456")) {
                    if (conn.isValid(5)) {
                        Thread.sleep(10000);
                        log.info("PostgreSQL is ready!");
                        return;
                    }
                }
            } catch (Exception e) {
                attempts++;
                if (attempts == maxAttempts) {
                    throw new RuntimeException("Could not connect to PostgreSQL after " + maxAttempts + " attempts", e);
                }
                log.info("Waiting for PostgreSQL to be ready... Attempt {}/{}", attempts, maxAttempts);
                Thread.sleep(retryInterval);
            }
        }
    }
}