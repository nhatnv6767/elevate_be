package com.elevatebanking.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.com.github.dockerjava.core.DefaultDockerClientConfig;
import org.testcontainers.shaded.com.github.dockerjava.core.DockerClientConfig;
import org.testcontainers.shaded.com.github.dockerjava.core.DockerClientImpl;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@DependsOn("dockerConfig")
public class PostgresInitializer {
//    private static final Logger log = LoggerFactory.getLogger(PostgresInitializer.class);
//    private final DockerClient dockerClient;
//
//
//    public PostgresInitializer(@Value("${docker.host:tcp://192.168.1.128:2375}") String dockerHost) {
//        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
//                .withDockerHost(dockerHost)
//                .withDockerTlsVerify(false)
//                .build();
//
//        dockerClient = DockerClientImpl.getInstance(config);
//    }
//
//    @PostConstruct
//    public void initializePostgres() throws InterruptedException {
//        try {
//            // Check if container exists
//            List<Container> existingContainers = dockerClient.listContainersCmd()
//                    .withNameFilter(Collections.singleton("elevate-banking-postgres"))
//                    .withShowAll(true)
//                    .exec();
//
//            // Remove if exists
//            for (Container container : existingContainers) {
//                log.info("Removing existing container: {}", container.getId());
//                dockerClient.removeContainerCmd(container.getId())
//                        .withForce(true)
//                        .exec();
//            }
//
//            // Pull latest PostgreSQL image
//            dockerClient.pullImageCmd("postgres:latest")
//                    .exec(new PullImageResultCallback())
//                    .awaitCompletion();
//
//            // Create volume
//            String volumeName = "postgres_data";
//            try {
//                dockerClient.createVolumeCmd()
//                        .withName(volumeName)
//                        .exec();
//            } catch (Exception e) {
//                log.warn("Volume might already exist: {}", e.getMessage());
//            }
//
//            // Create container
//            CreateContainerResponse container = dockerClient.createContainerCmd("postgres:latest")
//                    .withName("elevate-banking-postgres")
//                    .withHostConfig(HostConfig.newHostConfig()
//                            .withPortBindings(PortBinding.parse("5432:5432"))
//                            .withBinds(new Bind(volumeName, new Volume("/var/lib/postgresql/data"))))
//                    .withEnv(
//                            "POSTGRES_DB=elevate_banking",
//                            "POSTGRES_USER=root",
//                            "POSTGRES_PASSWORD=123456",
//                            "POSTGRES_HOST_AUTH_METHOD=trust"
//                    )
//                    .withExposedPorts(ExposedPort.tcp(5432))
//                    .exec();
//
//            // Start container
//            dockerClient.startContainerCmd(container.getId()).exec();
//
//            // Wait for PostgreSQL to be ready
//            waitForPostgres();
//
//            log.info("PostgreSQL container is ready!");
//        } catch (Exception e) {
//            log.error("Failed to initialize PostgreSQL", e);
//            throw new RuntimeException(e);
//        }
//    }
//
//    private void waitForPostgres() throws InterruptedException {
//        int maxAttempts = 60;
//        int attempt = 0;
//        boolean isReady = false;
//
//        while (!isReady && attempt < maxAttempts) {
//            try {
//                attempt++;
//                log.info("Checking PostgreSQL connection attempt {}/{}", attempt, maxAttempts);
//
//                try (Socket socket = new Socket()) {
//                    socket.connect(new InetSocketAddress("192.168.1.128", 5432), 1000);
//                }
//
//                try (Connection conn = DriverManager.getConnection(
//                        "jdbc:postgresql://192.168.1.128:5432/elevate_banking",
//                        "root",
//                        "123456"
//                )) {
//                    if (conn.isValid(5)) {
//                        // Try to create database if it doesn't exist
//                        try (Statement stmt = conn.createStatement()) {
//                            stmt.execute("CREATE DATABASE IF NOT EXISTS elevate_banking");
//                        }
//                        isReady = true;
//                        continue;
//                    }
//                }
//            } catch (Exception e) {
//                log.warn("PostgreSQL not ready yet: {}", e.getMessage());
//            }
//            Thread.sleep(2000);
//        }
//
//        if (!isReady) {
//            throw new RuntimeException("PostgreSQL failed to start after " + maxAttempts + " attempts");
//        }
//    }
}
