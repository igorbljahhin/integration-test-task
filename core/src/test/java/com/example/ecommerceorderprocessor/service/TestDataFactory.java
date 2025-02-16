package com.example.ecommerceorderprocessor.service;

import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderItem;
import com.example.ecommerceorderprocessor.model.OrderStatusEnum;

import java.time.LocalDateTime;
import java.util.List;

public class TestDataFactory {

    public static Order createEmptyOrder(OrderStatusEnum status) {
        Order order = createSampleOrder(status);
        order.setOrderItems(List.of());

        return order;
    }

    public static Order createSampleOrder(OrderStatusEnum status) {
        Order order = new Order();
        order.setOrderId("ORD-123");
        order.setCustomerId("CUST-456");
        order.setStatus(status);
        order.setCurrencyCode("USD");
        order.setOrderTotal(150.0f);
        order.setOrderPaid(150.0f);
        order.setCreationTimestamp(LocalDateTime.now());
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setOrderItems(List.of(createSampleOrderItem()));

        return order;
    }

    public static OrderItem createSampleOrderItem() {
        OrderItem item = new OrderItem();
        item.setProductId("PROD-789");
        item.setProductName("Sample Product");
        item.setPrice(75.0f);
        item.setQuantity(2);

        return item;
    }
}