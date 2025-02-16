package com.example.ecommerceorderprocessor.config;

import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.service.OrderProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.aggregator.CorrelationStrategy;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.store.MessageGroupStore;
import org.springframework.messaging.MessageChannel;

import java.util.concurrent.Executors;

@Configuration
@RequiredArgsConstructor
public class IntegrationConfig {

    private final ConnectionFactory connectionFactory;
    private final MessageGroupStore messageStore;
    private final ObjectMapper objectMapper;
    private final CorrelationStrategy orderCorrelationStrategy;
    private final OrderProcessor orderProcessor;

    @Bean
    public IntegrationFlow amqpInbound() {
        return IntegrationFlow.from(
                        Amqp.inboundAdapter(connectionFactory, orderCreatedQueue())
                                .messageConverter(jsonMessageConverter())
                )
                .log(LoggingHandler.Level.INFO, "Received order message")
                .transform(source -> objectMapper.convertValue(source, Order.class))
                .log(LoggingHandler.Level.INFO, m -> "Processing order #" + ((Order) m.getPayload()).getOrderId())
                .channel(orderInputChannel())
                .get();
    }

    @Bean
    public MessageChannel crmOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel financialOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue("orderCreated-queue", true);
    }

    @Bean
    public MessageChannel orderInputChannel() {
        return new ExecutorChannel(Executors.newFixedThreadPool(10));
    }

    @Bean
    public IntegrationFlow processOrderFlow() {
        return IntegrationFlow.from(processedOrderChannel())
                .handle(orderProcessor)
                .get();
    }

    @Bean
    public MessageChannel processedOrderChannel() {
        return new ExecutorChannel(Executors.newFixedThreadPool(10));
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        final RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());

        return template;
    }

    @Bean
    public IntegrationFlow resequencingFlow() {
        return IntegrationFlow.from(orderInputChannel())
                .resequence(spec -> spec
                        .messageStore(messageStore)
                        .correlationStrategy(orderCorrelationStrategy)
                        .messageStore(messageStore)
                        .releasePartialSequences(true)
                        .groupTimeout(5000)
                        .sendPartialResultOnExpiry(true)
                )
                .channel(processedOrderChannel())
                .get();
    }
}
