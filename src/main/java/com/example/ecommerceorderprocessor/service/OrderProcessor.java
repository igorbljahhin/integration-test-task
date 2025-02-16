package com.example.ecommerceorderprocessor.service;

import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderProcessor {
    private final CrmService crmService;
    private final FinancialService financialService;

    @ServiceActivator
    public void process(final Order order) {
        // send to CRM System (all order statuses)
        crmService.sendOrderUpdate(order);

        // send to Financial System (only PAID or CANCELLED orders)
        if (order.getStatus() == OrderStatusEnum.PAID || order.getStatus() == OrderStatusEnum.CANCELLED) {
            financialService.writeOrderToFile(order);
        }

        // no return value - the processing is complete
    }
}