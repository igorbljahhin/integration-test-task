package com.example.ecommerceorderprocessor.model.financial;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinancialOrderRecord {
    @CsvBindByPosition(position = 7)
    @CsvBindByName(column = "currency_code")
    private String currencyCode;
    @CsvBindByPosition(position = 0)
    @CsvBindByName(column = "order_id")
    private String orderId;
    @CsvBindByPosition(position = 6)
    @CsvBindByName(column = "order_paid_amount")
    private float orderPaidAmount;
    @CsvBindByPosition(position = 5)
    @CsvBindByName(column = "order_total")
    private float orderTotal;
    @CsvBindByPosition(position = 2)
    @CsvBindByName(column = "product_id")
    private String productId;
    @CsvBindByPosition(position = 1)
    @CsvBindByName(column = "product_name")
    private String productName;
    @CsvBindByPosition(position = 4)
    @CsvBindByName(column = "product_price")
    private float productPrice;
    @CsvBindByPosition(position = 3)
    @CsvBindByName(column = "quantity")
    private int quantity;
}