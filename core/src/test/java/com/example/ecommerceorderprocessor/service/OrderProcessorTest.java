package com.example.ecommerceorderprocessor.service;

import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProcessorTest {

    @Mock
    private CrmService crmService;

    @Mock
    private FinancialService financialService;

    @InjectMocks
    private OrderProcessor orderProcessor;

    @Test
    void shouldNotSendOtherStatusesToFinancial() {
        // Test statuses that should not go to financial
        OrderStatusEnum[] nonFinancialStatuses = {OrderStatusEnum.PENDING, OrderStatusEnum.UPDATED, OrderStatusEnum.CONFIRMED, OrderStatusEnum.SHIPPED};

        for (OrderStatusEnum status : nonFinancialStatuses) {
            Order order = TestDataFactory.createSampleOrder(status);
            orderProcessor.process(order);
            verify(financialService, never()).writeOrderToFile(order);
        }
    }

    @Test
    void shouldSendAllOrdersToCrm() {
        // Test each status
        for (OrderStatusEnum status : OrderStatusEnum.values()) {
            Order order = TestDataFactory.createSampleOrder(status);
            orderProcessor.process(order);
            verify(crmService, times(1)).sendOrderUpdate(order);
        }
    }

    @Test
    void shouldSendOnlyCancelledOrdersToFinancial() {
        Order cancelledOrder = TestDataFactory.createSampleOrder(OrderStatusEnum.CANCELLED);
        orderProcessor.process(cancelledOrder);
        verify(financialService, times(1)).writeOrderToFile(cancelledOrder);
    }

    @Test
    void shouldSendOnlyPaidOrdersToFinancial() {
        Order paidOrder = TestDataFactory.createSampleOrder(OrderStatusEnum.PAID);
        orderProcessor.process(paidOrder);
        verify(financialService, times(1)).writeOrderToFile(paidOrder);
    }
}