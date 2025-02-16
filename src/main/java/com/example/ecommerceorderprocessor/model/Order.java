package com.example.ecommerceorderprocessor.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Order {
    private LocalDateTime creationTimestamp;
    private String currencyCode;
    private String customerId;
    private String orderId;
    private List<OrderItem> orderItems;
    private float orderPaid;
    private float orderTotal;
    private OrderStatusEnum status;
    private LocalDateTime updatedTimestamp;
}