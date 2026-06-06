package com.tcl.backend.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// RabbitMQ configuration for backend messaging.
// Matches the RabbitMQ/on-off-chain bridge contract:
// mempool_exchange -> mempool_queue, with DLQs for expired/failed messages.
@Configuration
public class RabbitConfig {

    @Bean
    // Dead-letter queue for mempool messages that expire or cannot be processed.
    Queue mempoolDlq(RabbitQueueProperties properties) {
        return QueueBuilder.durable(properties.mempoolDlq()).build();
    }

    @Bean
    // Dead-letter queue for event-bus messages that fail listener processing.
    Queue eventBusDlq(RabbitQueueProperties properties) {
        return QueueBuilder.durable(properties.eventBusDlq()).build();
    }

    @Bean
    // Active outbound mempool queue. TTL matches teammate RabbitMQ bridge: 15 minutes.
    Queue mempoolQueue(RabbitQueueProperties properties) {
        return QueueBuilder.durable(properties.mempoolQueue())
                .withArgument("x-message-ttl", 900000)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", properties.mempoolDlq())
                .build();
    }

    @Bean
    // Active inbound Event Bus queue. Backend listens here for on-chain results.
    Queue eventBusQueue(RabbitQueueProperties properties) {
        return QueueBuilder.durable(properties.eventBusQueue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", properties.eventBusDlq())
                .build();
    }

    @Bean
    // Direct exchange used by the on/off-chain RabbitMQ bridge for mempool publishing.
    DirectExchange mempoolExchange(RabbitQueueProperties properties) {
        return new DirectExchange(properties.mempoolExchange());
    }

    @Bean
    // Route messages with routing key mempool_queue into the mempool queue.
    Binding bindingMempoolQueue(RabbitQueueProperties properties) {
        return BindingBuilder.bind(mempoolQueue(properties))
                .to(mempoolExchange(properties))
                .with(properties.mempoolQueue());
    }

    @Bean
    // Convert Java objects to JSON when publishing, and JSON back to Java objects when consuming.
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
