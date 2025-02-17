package com.example.ecommerceorderprocessor.service;

import com.example.ecommerceorderprocessor.config.AppConfig;
import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialServiceTest {

    @Mock
    private AppConfig appConfig;
    @Mock
    private AppConfig.Financial financialConfig;
    @InjectMocks
    private FinancialService financialService;
    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        when(appConfig.getFinancial()).thenReturn(financialConfig);
        when(financialConfig.getOutputDirectory()).thenReturn(tempDir.toString());
        when(financialConfig.getMaxRecordsPerFile()).thenReturn(1000);
        when(financialConfig.getFileNamePattern()).thenReturn("fin_orders_{datetime:ddMMyyyyHHmmss}.csv");
    }

    @Test
    void shouldAppendToExistingFile() throws IOException {
        // Prepare
        Order firstOrder = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);
        Order secondOrder = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.CANCELLED);

        // Act
        financialService.writeOrderToFile(firstOrder);
        financialService.writeOrderToFile(secondOrder);

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("fin_orders_"));
        assertNotNull(files);
        assertEquals(1, files.length);

        List<String> lines = Files.readAllLines(files[0].toPath());
        assertEquals(3, lines.size()); // Header + two data lines
    }

    @Test
    void shouldCreateNewFileWhenLimitReached() {
        // Prepare
        when(financialConfig.getMaxRecordsPerFile()).thenReturn(1);
        Order firstOrder = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);
        Order secondOrder = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.CANCELLED);

        // Act
        financialService.writeOrderToFile(firstOrder);

        // let's sleep a bit, otherwise the files will have same name
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {
        }

        financialService.writeOrderToFile(secondOrder);

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("fin_orders_"));
        assertNotNull(files);
        assertEquals(2, files.length);
    }

    @Test
    void shouldCreateNewFileWhenNoneExists() throws IOException {
        // Prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);

        // Act
        financialService.writeOrderToFile(order);

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("fin_orders_"));
        assertNotNull(files);
        assertEquals(1, files.length);

        List<String> lines = Files.readAllLines(files[0].toPath());
        assertEquals(2, lines.size()); // Header + one data line
        assertTrue(lines.getFirst().contains("order_id,product_name,product_id"));
    }

    @Test
    void shouldGenerateCorrectFileName() {
        // Prepare
        String filePattern = "test_orders_{datetime:ddMMyyyyHHmm}.csv";
        when(financialConfig.getFileNamePattern()).thenReturn(filePattern);
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);

        // Act
        financialService.writeOrderToFile(order);

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("test_orders_"));
        assertNotNull(files);
        assertEquals(1, files.length);
        assertTrue(files[0].getName().matches("test_orders_\\d{12}\\.csv"));
    }

    @Test
    void shouldHandleEmptyOrderItems() {
        // Prepare
        Order order = TestDataFactory.createEmptyOrder(OrderStatusEnum.PAID);

        // Act
        financialService.writeOrderToFile(order);

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("fin_orders_"));
        assertNotNull(files);
        assertEquals(0, files.length);
    }

    @Test
    void shouldHandleMultipleItemsInOrder() throws IOException {
        // Prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);
        order.setOrderItems(List.of(
                TestDataFactory.createSampleOrderItem(),
                TestDataFactory.createSampleOrderItem()
        ));

        // Act
        financialService.writeOrderToFile(order);

        // Assert
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("fin_orders_"));
        assertNotNull(files);
        assertEquals(1, files.length);

        List<String> lines = Files.readAllLines(files[0].toPath());
        assertEquals(3, lines.size()); // Header + two data lines (one per item)
    }

    @Test
    void shouldHandleNonExistentOutputDirectory() {
        // Prepare
        when(financialConfig.getOutputDirectory()).thenReturn(tempDir.resolve("nonexistent").toString());
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);

        // Act & Assert
        assertDoesNotThrow(() -> financialService.writeOrderToFile(order));
    }
}