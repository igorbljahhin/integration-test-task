package com.example.ecommerceorderprocessor.service;

import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container
    private static final GenericContainer<?> mockCrm = new GenericContainer<>("stoplight/prism:latest")
            .withExposedPorts(4010)
            .withCommand("mock -h 0.0.0.0 /tmp/api.yaml")
            .withFileSystemBind(getSwaggerFile().getAbsolutePath(), "/tmp/api.yaml")
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("mock-crm")))
            .waitingFor(Wait.forHttp("/").forStatusCode(404));
    @Container
    private static final GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:3-management-alpine")
            .withExposedPorts(5672, 15672)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("rabbitmq")))
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    private static File getSwaggerFile() {
        File swaggerFile;

        try {
            swaggerFile = File.createTempFile("swagger", ".yaml");
            swaggerFile.deleteOnExit();

            Files.copy(
                    new ClassPathResource("CRM_Swagger_Definition.yaml").getInputStream(),
                    swaggerFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Swagger file for mock CRM", e);
        }

        return swaggerFile;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("app.crm.api-url", () -> "http://" + mockCrm.getHost() + ":" + mockCrm.getMappedPort(4010));
    }
}
