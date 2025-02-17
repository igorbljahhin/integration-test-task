package com.example.ecommerceorderprocessor.service;

import com.example.crm.model.OrderUpdateRequest;
import com.example.ecommerceorderprocessor.config.AppConfig;
import com.example.ecommerceorderprocessor.config.IntegrationTestConfig;
import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderStatusEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Slf4j
@Import(IntegrationTestConfig.class)
class MessageProcessingIntegrationITTest extends AbstractIntegrationTest {


    private static final String QUEUE_NAME = "orderCreated-queue";
    @TempDir
    Path tempDir;
    @Autowired
    private AppConfig appConfig;
    @Autowired
    private MessageGroupStore messageStore;
    private MockRestServiceServer mockServer;
    @Autowired
    private ObjectMapper objectMapper;
    private CountDownLatch orderLatch;
    private List<String> processedOrderStatuses;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        processedOrderStatuses = new ArrayList<>();

        mockServer = MockRestServiceServer.createServer(restTemplate);

        // create financial output directory
        File financialOutputDirectory = new File(tempDir.toFile(), "financial-output");
        financialOutputDirectory.mkdirs();

        appConfig.getFinancial().setOutputDirectory(financialOutputDirectory.getAbsolutePath());
        log.info("Set financial output directory to: {}", financialOutputDirectory.getAbsolutePath());

        // clear message store
        messageStore.removeMessageGroup(QUEUE_NAME);

        // ensure queue exists and is empty
        rabbitAdmin.declareQueue(new org.springframework.amqp.core.Queue(QUEUE_NAME, true, false, false));
        rabbitAdmin.purgeQueue(QUEUE_NAME);

        // verify queue is ready
        Properties queueProperties = rabbitAdmin.getQueueProperties(QUEUE_NAME);
        assertNotNull(queueProperties, "Queue should be created and available");
        log.info("Queue {} is ready with properties: {}", QUEUE_NAME, queueProperties);
    }

    @Test
    void shouldMaintainOrderSequence() throws Exception {
        // prepare two orders with same ID
        Order firstOrder = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PENDING);
        Order secondOrder = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);

        orderLatch = new CountDownLatch(2);

        // configure mock
        mockServer.expect(manyTimes(), requestTo(matchesPattern(appConfig.getCrm().getApiUrl() + "/customers/.+/orders")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.externalOrderId").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.financials").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andRespond(request -> {
                    OrderUpdateRequest receivedOrder = objectMapper.readValue(request.getBody().toString(), OrderUpdateRequest.class);
                    log.info("Received CRM request for order {} with status: {}", receivedOrder.getExternalOrderId(), receivedOrder.getStatus());
                    processedOrderStatuses.add(receivedOrder.getStatus().getValue());
                    orderLatch.countDown();

                    return withSuccess().createResponse(request);
                });

        // act
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        // send first message
        log.info("Sending first order {} with status: {}", firstOrder.getOrderId(), firstOrder.getStatus());
        Message firstMessage = new Message(objectMapper.writeValueAsBytes(firstOrder), messageProperties);
        rabbitTemplate.send(QUEUE_NAME, firstMessage);

        // verify first message is in queue
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Properties queueProps = rabbitAdmin.getQueueProperties(QUEUE_NAME);
            assertNotNull(queueProps);
            log.info("Queue state after first message: {}", queueProps);
        });

        // send second message
        log.info("Sending second order {} with status: {}", secondOrder.getOrderId(), secondOrder.getStatus());
        Message secondMessage = new Message(objectMapper.writeValueAsBytes(secondOrder), messageProperties);
        rabbitTemplate.send(QUEUE_NAME, secondMessage);

        // verify both messages are being processed
        boolean processed = orderLatch.await(20, TimeUnit.SECONDS);
        log.info("Order processing completed: {}, processed orders: {}", processed, processedOrderStatuses);

        assertTrue(processed, "Timeout waiting for orders to be processed");
        assertEquals(2, processedOrderStatuses.size(), "Should process both orders");
        assertEquals("pending", processedOrderStatuses.get(0), "First status should be pending");
        assertEquals("paid", processedOrderStatuses.get(1), "Second status should be paid");

        // Verify queue is empty after processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Properties queueProps = rabbitAdmin.getQueueProperties(QUEUE_NAME);
            assertNotNull(queueProps);
            assertEquals(0, (Integer) queueProps.get("QUEUE_MESSAGE_COUNT"), "Queue should be empty after processing");
        });
    }

    @Test
    void shouldOnlySendToCrmForNonFinancialStatuses() throws Exception {
        // prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PENDING);

        orderLatch = new CountDownLatch(1);

        mockServer.expect(once(), requestTo(matchesPattern(appConfig.getCrm().getApiUrl() + "/customers/.+/orders")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.externalOrderId").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.financials").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andRespond(request -> {
                    OrderUpdateRequest receivedOrder = objectMapper.readValue(request.getBody().toString(), OrderUpdateRequest.class);
                    log.info("Received CRM request for order {} with status: {}", receivedOrder.getExternalOrderId(), receivedOrder.getStatus());
                    processedOrderStatuses.add(receivedOrder.getStatus().getValue());
                    orderLatch.countDown();

                    return withSuccess().createResponse(request);
                });

        // act
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(objectMapper.writeValueAsBytes(order), messageProperties);

        rabbitTemplate.send(QUEUE_NAME, message);

        boolean processed = orderLatch.await(20, TimeUnit.SECONDS);
        log.info("Order processing completed: {}, processed orders: {}", processed, processedOrderStatuses);

        // assert
        // 1. verify CRM call
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> mockServer.verify());

        // 2. verify no financial file was created
        Thread.sleep(2000); // Wait a bit to ensure no file is created
        File[] files = new File(tempDir.toFile(), "financial-output")
                .listFiles((dir, name) -> name.startsWith("fin_orders_"));
        assertEquals(0, files != null ? files.length : 0);
    }

    @Test
    void shouldProcessCancelledOrderCompletely() throws Exception {
        // prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.CANCELLED);

        orderLatch = new CountDownLatch(1);

        mockServer.expect(once(), requestTo(matchesPattern(appConfig.getCrm().getApiUrl() + "/customers/.+/orders")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.externalOrderId").value("ORD-123"))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andRespond(request -> {
                    OrderUpdateRequest receivedOrder = objectMapper.readValue(request.getBody().toString(), OrderUpdateRequest.class);
                    log.info("Received CRM request for order {} with status: {}", receivedOrder.getExternalOrderId(), receivedOrder.getStatus());
                    processedOrderStatuses.add(receivedOrder.getStatus().getValue());
                    orderLatch.countDown();

                    return withSuccess().createResponse(request);
                });

        // act
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(objectMapper.writeValueAsBytes(order), messageProperties);

        rabbitTemplate.send(QUEUE_NAME, message);

        boolean processed = orderLatch.await(20, TimeUnit.SECONDS);
        log.info("Order processing completed: {}, processed orders: {}", processed, processedOrderStatuses);

        // assert
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> mockServer.verify());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    File[] files = new File(tempDir.toFile(), "financial-output")
                            .listFiles((dir, name) -> name.startsWith("fin_orders_"));

                    return files != null && files.length > 0;
                });
    }

    @Test
    void shouldProcessPaidOrderCompletely() throws Exception {
        // prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);

        orderLatch = new CountDownLatch(1);

        mockServer.expect(once(), requestTo(matchesPattern(appConfig.getCrm().getApiUrl() + "/customers/.+/orders")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.externalOrderId").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.financials").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andRespond(request -> {
                    OrderUpdateRequest receivedOrder = objectMapper.readValue(request.getBody().toString(), OrderUpdateRequest.class);
                    log.info("Received CRM request for order {} with status: {}", receivedOrder.getExternalOrderId(), receivedOrder.getStatus());
                    processedOrderStatuses.add(receivedOrder.getStatus().getValue());
                    orderLatch.countDown();

                    return withSuccess().createResponse(request);
                });

        // act
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(objectMapper.writeValueAsBytes(order), messageProperties);

        rabbitTemplate.send(QUEUE_NAME, message);

        boolean processed = orderLatch.await(20, TimeUnit.SECONDS);
        log.info("Order processing completed: {}, processed orders: {}", processed, processedOrderStatuses);

        // assert
        // 1. verify CRM call
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> mockServer.verify());

        // 2. verify Financial file creation
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    File financialOutputDirectory = new File(tempDir.toFile(), "financial-output");

                    File[] files = financialOutputDirectory
                            .listFiles((dir, name) -> name.startsWith("fin_orders_"));

                    log.info("Checking financial files in the directory {}. Found: {}", financialOutputDirectory.getAbsolutePath(), files != null ? files.length : 0);

                    if (files != null && files.length > 0) {
                        log.info("Files found: {}", (Object[]) files);
                    }

                    return files != null && files.length > 0;
                });
    }
}
