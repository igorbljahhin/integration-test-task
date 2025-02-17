package com.example.ecommerceorderprocessor.service;

import com.example.crm.model.OrderUpdateRequest;
import com.example.ecommerceorderprocessor.config.AppConfig;
import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrmServiceTest {

    private static final String API_URL = "http://localhost:4010";
    @Mock
    private AppConfig appConfig;

    @Mock
    private AppConfig.CRM crmConfig;

    @InjectMocks
    private CrmService crmService;
    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        when(appConfig.getCrm()).thenReturn(crmConfig);
        when(crmConfig.getApiUrl()).thenReturn(API_URL);
    }

    @Test
    void shouldConstructCorrectCrmUrl() {
        // Prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);
        String expectedUrl = API_URL + "/customers/" + order.getCustomerId() + "/orders";

        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        crmService.sendOrderUpdate(order);

        // Verify
        verify(restTemplate).exchange(
                eq(expectedUrl),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    void shouldCorrectlyConvertOrderToCrmRequest() {
        // Prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);
        ArgumentCaptor<HttpEntity<OrderUpdateRequest>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act
        crmService.sendOrderUpdate(order);

        // Verify
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.PUT),
                requestCaptor.capture(),
                eq(Void.class)
        );

        OrderUpdateRequest request = requestCaptor.getValue().getBody();
        assertNotNull(request);
        assertEquals(order.getOrderId(), request.getExternalOrderId());
        assertEquals(order.getStatus().getCode(), request.getStatus().getValue());
        assertEquals(order.getCurrencyCode(), request.getFinancials().getCurrencyCode());
        assertEquals(order.getOrderTotal(), request.getFinancials().getOrderTotal(), 0.001);
        assertEquals(order.getOrderPaid(), request.getFinancials().getOrderPaid(), 0.001);
        assertEquals(order.getOrderItems().size(), request.getItems().size());
    }

    @Test
    void shouldHandleFailedCrmUpdate() {
        // Prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> crmService.sendOrderUpdate(order));
    }

    @Test
    void shouldHandleSuccessfulCrmUpdate() {
        // Prepare
        Order order = TestDataFactory.createSampleOrder("ORD-123", OrderStatusEnum.PAID);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Act & Assert
        assertDoesNotThrow(() -> crmService.sendOrderUpdate(order));
    }
}