package com.elevatebanking.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
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
import java.util.Collections;

@Component
@DependsOn("dockerConfig")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final DockerClient dockerClient;

    public DatabaseInitializer(@Value("${docker.host:tcp://192.168.1.128:2375}") String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(false)
                .build();

        dockerClient = DockerClientImpl.getInstance(config);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializePostgres();
    }

    private void initializePostgres() throws Exception {
        String containerName = "elevate-banking-postgres";

        // Remove existing container
        dockerClient.listContainersCmd()
                .withNameFilter(Collections.singleton(containerName))
                .withShowAll(true)
                .exec()
                .forEach(container -> {
                    try {
                        dockerClient.removeContainerCmd(container.getId())
                                .withForce(true)
                                .exec();
                        log.info("Removed existing container: {}", container.getId());
                    } catch (Exception e) {
                        log.warn("Failed to remove container: {}", e.getMessage());
                    }
                });

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
                        .withPortBindings(PortBinding.parse("5432:5432")))
                .withExposedPorts(ExposedPort.tcp(5432))
                .exec();

        // Start container
        dockerClient.startContainerCmd(container.getId()).exec();

        // Wait for PostgreSQL to be ready
        waitForPostgres();
    }

    private void waitForPostgres() throws Exception {
        int attempts = 0;
        int maxAttempts = 60;
        while (attempts < maxAttempts) {
            try {
                Class.forName("org.postgresql.Driver");
                try (Connection conn = DriverManager.getConnection(
                        "jdbc:postgresql://192.168.1.128:5432/elevate_banking",
                        "root", "123456")) {
                    if (conn.isValid(5)) {
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
                Thread.sleep(2000);
            }
        }
    }
}