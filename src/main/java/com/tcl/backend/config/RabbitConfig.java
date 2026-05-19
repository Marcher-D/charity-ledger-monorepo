package com.tcl.backend.config;

// This File is to configure Rabbit Message Template

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// import java.util.HashMap;
// import java.util.Map; 
// These two are used to mapping arguments key-value so that later will 
// be set as withArguments in the QueueBuilder

@Configuration 
public class RabbitConfig {
    
    // Define Queue Naming Constants 
    public static final String MEMPOOL_QUEUE = "mempool_queue";
    public static final String MEMPOOL_DLQ = "mempool_dlq";
    public static final String EVENT_BUS_QUEUE = "event_bus_queue";
    public static final String EVENT_BUS_DLQ  = "event_bus_dlq";

    // Dead Letter Queues
    @Bean 
    public Queue mempoolDLQ() {
        return QueueBuilder.durable(MEMPOOL_DLQ).build();
    }
    @Bean 
    public Queue eventBusDLQ() {
        return QueueBuilder.durable(EVENT_BUS_DLQ).build();
    }
    // Active Mempool Queue (With TTL - 15 minutes and DLQ logic)
    @Bean
    public Queue mempoolQueue() {
        return QueueBuilder.durable(MEMPOOL_QUEUE)
            .withArgument("x-message-ttl", 900000) // 15 minutes lifetime
            .withArgument("x-dead-letter-exchange", "") // Direct to Default Exchange -> to the name of that queue
            .withArgument("x-dead-letter-routing-key", MEMPOOL_DLQ)
            .build();
    }
    // The Active Event Bus Queue (With DLQ logic attached)
    @Bean 
    public Queue eventBusQueue() {
        return QueueBuilder.durable(EVENT_BUS_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", EVENT_BUS_DLQ)
            .build();
    }

    // Java-To-JSON Translator
    @Bean 
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    // Configure RabbitTemplate
    @Bean 
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
