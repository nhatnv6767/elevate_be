package com.elevatebanking.entity.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.net.Socket;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.dockerjava.api.model.Network;
import jakarta.annotation.PreDestroy;
import com.github.dockerjava.api.model.Container;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DockerConfig {

    @Value("${docker.host:tcp://192.168.1.128:2375}")
    private String dockerHost;

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    private static final int CONTAINER_STARTUP_TIMEOUT = 120;
    private static final int CONNECTION_TIMEOUT = 2000;
    private static final int RETRY_DELAY = 1000;

    @PostConstruct
    public void initializeDockerServices() {
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

        createPostgresContainer(dockerClient);
        waitForPostgresqlContainer();
    }

    private void createPostgresContainer(DockerClient dockerClient) {
        String containerName = "elevate-banking-postgres";
        try {
            // Xóa container cũ nếu tồn tại
            List<Container> containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withShowAll(true)
                    .exec();
            if (!containers.isEmpty()) {
                String containerId = containers.get(0).getId();
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }

            // Tạo network mới
            checkAndCreateNetwork(dockerClient);

            // Pull image và tạo container
            dockerClient.pullImageCmd("postgres:latest")
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            CreateContainerResponse container = dockerClient.createContainerCmd("postgres:latest")
                    .withName(containerName)
                    .withEnv(
                            "POSTGRES_DB=elevate_banking",
                            "POSTGRES_USER=root",
                            "POSTGRES_PASSWORD=123456"
                    )
                    .withHostConfig(HostConfig.newHostConfig()
                            .withNetworkMode("elevate-banking-network")
                            .withPortBindings(PortBinding.parse("5432:5432"))
                            .withMemory(512 * 1024 * 1024L)
                            .withCpuCount(1L))
                    .exec();

            // Khởi động container
            dockerClient.startContainerCmd(container.getId()).exec();

            // Đợi container khởi động hoàn toàn
            Thread.sleep(30000);

            // Kiểm tra container đã sẵn sàng
            boolean isReady = false;
            int attempts = 0;
            while (!isReady && attempts < 60) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("localhost", 5432), 1000);
                    isReady = true;
                } catch (Exception e) {
                    Thread.sleep(1000);
                    attempts++;
                }
            }

            if (!isReady) {
                throw new RuntimeException("PostgreSQL container không sẵn sàng sau 60 giây");
            }
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo PostgreSQL container", e);
        }
    }

    private void waitForPostgresqlContainer() {
        int maxAttempts = 120;
        int attempt = 0;
        while (attempt < maxAttempts) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", 5432), 2000);
                Thread.sleep(10000);
                try (Connection conn = DriverManager.getConnection(
                        "jdbc:postgresql://localhost:5432/elevate_banking",
                        "root",
                        "123456")) {
                    if (conn.isValid(5)) {
                        return;
                    }
                }
            } catch (Exception e) {
                attempt++;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new RuntimeException("PostgreSQL container không sẵn sàng sau 240 giây");
    }

    private boolean isContainerRunning(DockerClient dockerClient, String containerName) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withNameFilter(Collections.singleton(containerName))
                    .withShowAll(true)
                    .exec();

            if (!containers.isEmpty()) {
                String state = containers.get(0).getState();
                if (!"running".equalsIgnoreCase(state)) {
                    dockerClient.startContainerCmd(containers.get(0).getId()).exec();
                    Thread.sleep(5000);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkAndCreateNetwork(DockerClient dockerClient) {
        try {
            List<Network> networks = dockerClient.listNetworksCmd().exec();
            boolean networkExists = networks.stream()
                    .anyMatch(n -> n.getName().equals("elevate-banking-network"));
            if (!networkExists) {
                dockerClient.createNetworkCmd()
                        .withName("elevate-banking-network")
                        .withDriver("bridge")
                        .exec();
            }
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo network", e);
        }
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
}
