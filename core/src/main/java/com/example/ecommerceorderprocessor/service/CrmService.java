package com.example.ecommerceorderprocessor.service;

import com.example.crm.model.OrderUpdateRequest;
import com.example.crm.model.OrderUpdateRequestFinancials;
import com.example.crm.model.OrderUpdateRequestItemsInner;
import com.example.crm.model.OrderUpdateRequestItemsInnerProduct;
import com.example.ecommerceorderprocessor.config.AppConfig;
import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrmService {

    private final AppConfig appConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    private OrderUpdateRequestItemsInner convertOrderItem(final OrderItem orderItem) {
        // convert OrderItem to CRM Product
        final OrderUpdateRequestItemsInnerProduct product = new OrderUpdateRequestItemsInnerProduct();
        product.setId(orderItem.getProductId());
        product.setName(orderItem.getProductName());

        // convert OrderItem to CRM Item
        final OrderUpdateRequestItemsInner item = new OrderUpdateRequestItemsInner();
        item.setPrice(orderItem.getPrice());
        item.setQuantity(orderItem.getQuantity());
        item.setProduct(product);

        return item;
    }

    private OrderUpdateRequest createOrderUpdateRequest(final Order order) {
        // convert OrderItems to CRM Items
        final List<OrderUpdateRequestItemsInner> items = order.getOrderItems().stream()
                .map(this::convertOrderItem)
                .collect(Collectors.toList());

        // create financials object
        final OrderUpdateRequestFinancials financials = new OrderUpdateRequestFinancials();
        financials.setCurrencyCode(order.getCurrencyCode());
        financials.setOrderPaid(order.getOrderPaid());
        financials.setOrderTotal(order.getOrderTotal());

        // build the full request
        final OrderUpdateRequest request = new OrderUpdateRequest();
        request.setExternalOrderId(order.getOrderId());
        request.setStatus(OrderUpdateRequest.StatusEnum.fromValue(order.getStatus().getCode()));
        request.setFinancials(financials);
        request.setItems(items);

        return request;
    }

    public void sendOrderUpdate(Order order) {
        final String url = appConfig.getCrm().getApiUrl() + "/customers/" + order.getCustomerId() + "/orders";

        final OrderUpdateRequest request = createOrderUpdateRequest(order);

        final HttpEntity<OrderUpdateRequest> requestEntity = new HttpEntity<>(request);

        final ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                requestEntity,
                Void.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to update order in CRM: " + response.getStatusCode());
        }
    }
}
