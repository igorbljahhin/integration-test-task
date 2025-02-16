package com.example.ecommerceorderprocessor.config;

import com.example.ecommerceorderprocessor.model.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.aggregator.CorrelationStrategy;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.integration.store.SimpleMessageStore;
import org.springframework.messaging.MessageChannel;

@Configuration
public class OrderSequencingConfig {

    @Bean
    public MessageGroupStore messageStore() {
        return new SimpleMessageStore();
    }

    @Bean
    public CorrelationStrategy orderCorrelationStrategy() {
        return message -> {
            final Order order = (Order) message.getPayload();

            return order.getOrderId();
        };
    }

    @Bean
    public MessageChannel orderSequencingChannel() {
        return new QueueChannel();
    }
}