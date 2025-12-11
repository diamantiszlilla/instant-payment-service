package com.demo.instantpay;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractDbIntegrationTest {

    @SuppressWarnings("resource")
    protected static PostgreSQLContainer<?> createPostgresContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("instantpay_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
    }

    protected static void registerDatasourceProperties(DynamicPropertyRegistry registry, PostgreSQLContainer<?> container) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("pii.encryption.key", () -> "VTgDasP1R776SZNpu+5p+KYyznjZUaGbzBO2Pfs7rAY=");
    }

    protected static void startContainer(PostgreSQLContainer<?> container) {
        if (!container.isRunning()) {
            container.start();
        }
    }

    protected static void stopContainer(PostgreSQLContainer<?> container) {
        if (container.isRunning()) {
            container.stop();
        }
    }
}
