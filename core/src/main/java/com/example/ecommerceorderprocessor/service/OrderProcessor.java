package com.example.ecommerceorderprocessor.service;

import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessor {
    private final CrmService crmService;
    private final FinancialService financialService;

    @ServiceActivator
    public void process(final Order order) {
        log.debug("Starting to process order {} with status {}", order.getOrderId(), order.getStatus());

        try {
            // send to CRM System (all order statuses)
            log.debug("Sending order {} to CRM", order.getOrderId());
            crmService.sendOrderUpdate(order);
            log.debug("Successfully sent order {} to CRM", order.getOrderId());

            // send to Financial System (only PAID or CANCELLED orders)
            if (order.getStatus() == OrderStatusEnum.PAID || order.getStatus() == OrderStatusEnum.CANCELLED) {
                log.debug("Sending order {} to Financial system", order.getOrderId());
                financialService.writeOrderToFile(order);
                log.debug("Successfully sent order {} to Financial system", order.getOrderId());
            }
        } catch (Exception e) {
            log.error("Error processing order {}: {}", order.getOrderId(), e.getMessage(), e);

            throw e;
        }

        // no return value - the processing is complete
    }
}