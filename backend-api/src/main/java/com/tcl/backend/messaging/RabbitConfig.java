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
// This class is responsible for declaring all queues and exchanges on RabbitMQ
// when the backend starts. Blockchain nodes and other services can then read
// from / write to these queues without needing to declare them themselves.
//
// Flow:
//   backend-api  →  mempool_exchange  →  mempool_queue  →  on-chain-core (reads)
//   on-chain-core  →  event_bus_exchange  →  event_bus_queue  →  backend-api (reads)
@Configuration
public class RabbitConfig {

    // ─── Mempool side (backend → blockchain) ─────────────────────────────────

    @Bean
    // Durable classic queue. The mock mempool's pika consumer declares this queue
    // with exactly the same arguments; declarations MUST match byte-for-byte or
    // RabbitMQ rejects the second declarer with PRECONDITION_FAILED.
    Queue mempoolQueue(RabbitQueueProperties properties) {
        return QueueBuilder.durable(properties.mempoolQueue()).build();
    }

    @Bean
    // Direct exchange: routes messages to mempool_queue using the queue name as routing key.
    DirectExchange mempoolExchange(RabbitQueueProperties properties) {
        return new DirectExchange(properties.mempoolExchange());
    }

    @Bean
    // Binding: tells RabbitMQ to route messages from mempool_exchange → mempool_queue.
    Binding bindMempoolQueue(RabbitQueueProperties properties) {
        return BindingBuilder
                .bind(mempoolQueue(properties))
                .to(mempoolExchange(properties))
                .with(properties.mempoolQueue());
    }

    // ─── Event Bus side (blockchain → backend) ────────────────────────────────

    @Bean
    // Durable classic queue. The on-chain EventGateway (core code we cannot change)
    // declares this queue as `queueDeclare(name, true, false, false, null)` — a plain
    // durable classic queue. Our declaration must match exactly, otherwise whichever
    // side declares second gets PRECONDITION_FAILED and event delivery silently dies.
    Queue eventBusQueue(RabbitQueueProperties properties) {
        return QueueBuilder.durable(properties.eventBusQueue()).build();
    }

    @Bean
    // Direct exchange: routes messages to event_bus_queue using the queue name as routing key.
    DirectExchange eventBusExchange(RabbitQueueProperties properties) {
        return new DirectExchange(properties.eventBusExchange());
    }

    @Bean
    // Binding: tells RabbitMQ to route messages from event_bus_exchange → event_bus_queue.
    Binding bindEventBusQueue(RabbitQueueProperties properties) {
        return BindingBuilder
                .bind(eventBusQueue(properties))
                .to(eventBusExchange(properties))
                .with(properties.eventBusQueue());
    }

    // ─── Message Converter ────────────────────────────────────────────────────

    @Bean
    // Tells Spring AMQP to serialize Java objects → JSON when publishing,
    // and deserialize JSON → Java objects when consuming.
    // Used by both RabbitTemplate (publisher) and @RabbitListener (consumer).
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
