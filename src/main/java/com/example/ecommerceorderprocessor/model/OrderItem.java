package com.example.ecommerceorderprocessor.model;


import lombok.Data;

@Data
public class OrderItem {
    private float price;
    private String productId;
    private String productName;
    private int quantity;
}
