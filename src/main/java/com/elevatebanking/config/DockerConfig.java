package com.elevatebanking.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.io.Closeable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.net.Socket;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DockerConfig {

    @Value("${docker.host}")
    private String dockerHost;
    private final DockerClient dockerClient;

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    @Autowired
    public DockerConfig(@Value("${docker.host}") String dockerHost) {
        log.info("Initializing Docker with host: {}", dockerHost);
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
        return this.dockerClient;
    }

    @PostConstruct
    public void initializeDockerServices() throws Exception {
        log.info("Initializing Docker services...");

        try {
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

            // Pull image và tạo container
            dockerClient.pullImageCmd("postgres:latest")
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();

            CreateContainerResponse container = dockerClient.createContainerCmd("postgres:latest")
                    .withName(containerName)
                    .withEnv(
                            "POSTGRES_DB=elevate_banking",
                            "POSTGRES_USER=root",
                            "POSTGRES_PASSWORD=123456",
                            "POSTGRES_HOST_AUTH_METHOD=trust",
                            "LISTEN_ADDRESSES=*"
                    )
                    .withHostConfig(HostConfig.newHostConfig()
                            .withNetworkMode("bridge")
                            .withPortBindings(PortBinding.parse("5432:5432"))
                            .withPublishAllPorts(true)
                            .withMemory(512 * 1024 * 1024L))
                    .withExposedPorts(ExposedPort.tcp(5432))
                    .exec();

            // Khởi động container
            dockerClient.startContainerCmd(container.getId()).exec();

            logContainerOutput(dockerClient, container.getId());

            // Kiểm tra container status
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(container.getId()).exec();
            if (!containerInfo.getState().getRunning()) {
                throw new RuntimeException("Container failed to start: " + containerInfo.getState().getError());
            }
            // Đợi container khởi động
            Thread.sleep(10000);

            // Kiểm tra logs
            LogContainerCmd logContainerCmd = dockerClient.logContainerCmd(container.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(50);


        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo PostgreSQL container", e);
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

    private void waitForContainerToStart(DockerClient dockerClient, String containerId) {
        boolean isRunning = false;
        int attempts = 0;
        while (!isRunning && attempts < 30) {
            try {
                InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
                isRunning = containerInfo.getState().getRunning();
                if (!isRunning) {
                    Thread.sleep(2000);
                    attempts++;
                }
            } catch (Exception e) {
                log.warn("Lỗi khi kiểm tra trạng thái container: {}", e.getMessage());
                attempts++;
            }
        }
        if (!isRunning) {
            throw new RuntimeException("Container không thể khởi động sau 60 giây");
        }
    }

    private void logContainerOutput(DockerClient dockerClient, String containerId) {
        try {
            LogContainerCmd logContainerCmd = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTailAll();

            logContainerCmd.exec(new ResultCallback<Frame>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Frame frame) {
                    log.info(new String(frame.getPayload()));
                }


                @Override
                public void close() {
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Error in container logs", throwable);
                }

                @Override
                public void onComplete() {
                }
            });
        } catch (Exception e) {
            log.error("Error getting container logs", e);
        }
    }
}
