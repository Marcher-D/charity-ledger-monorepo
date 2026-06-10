package com.tcl.backend.config;

// This file configures all RabbitMQ infrastructure: Queues, Exchanges, Bindings, and the RabbitTemplate.
// Spring Boot reads application.properties to auto-create the ConnectionFactory (host, port, username, password).

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // ─── Naming Constants ────────────────────────────────────────────────────
    // Centralise names here so that publishers and listeners always reference
    // the same string — no typos across files.
    public static final String MEMPOOL_EXCHANGE   = "mempool_exchange";
    public static final String MEMPOOL_QUEUE      = "mempool_queue";

    public static final String EVENT_BUS_EXCHANGE = "event_bus_exchange";
    public static final String EVENT_BUS_QUEUE    = "event_bus_queue";

    // ─── Queues ──────────────────────────────────────────────────────────────

    // Mempool Queue: receives raw transactions from the Backend.
    // durable(true)     → the queue definition survives a RabbitMQ restart.
    // x-message-ttl     → a transaction that sits unprocessed for 15 minutes
    //                     is automatically dropped (expired). The blockchain
    //                     coordinator should detect this and emit a tx.expired
    //                     event through the event_bus before this deadline.
    //
    // NOTE — Quorum Queue (for production HA):
    //   To replicate this queue across a 3-node RabbitMQ cluster add:
    //   .withArgument("x-queue-type", "quorum")
    //   Quorum Queues are persistent by default, so no extra delivery_mode needed.
    @Bean
    public Queue mempoolQueue() {
        return QueueBuilder.durable(MEMPOOL_QUEUE)
                .withArgument("x-message-ttl", 900000) // 15 minutes in milliseconds
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    // Event Bus Queue: receives outcome events from the blockchain nodes.
    // (BLOCK_SUCCESS, BLOCK_FAIL, TX_FAILED, TX_EXPIRED, ...)
    // durable(true) → survives RabbitMQ restart; no TTL needed because nodes
    //                 push events immediately after processing.
    @Bean
    public Queue eventBusQueue() {
        return QueueBuilder.durable(EVENT_BUS_QUEUE)
                .build();
    }

    // ─── Exchanges ───────────────────────────────────────────────────────────
    // DirectExchange routes a message to the queue whose binding key
    // matches the routing key provided by the publisher exactly.

    @Bean
    public DirectExchange mempoolExchange() {
        return new DirectExchange(MEMPOOL_EXCHANGE);
    }

    @Bean
    public DirectExchange eventBusExchange() {
        return new DirectExchange(EVENT_BUS_EXCHANGE);
    }

    // ─── Bindings ────────────────────────────────────────────────────────────
    // A Binding is the wire between an Exchange and a Queue.
    // "with(QUEUE_NAME)" sets the routing key — the publisher must use the
    //  same key so the Exchange knows which Queue to deliver to.

    @Bean
    public Binding bindingMempoolQueue(Queue mempoolQueue, DirectExchange mempoolExchange) {
        return BindingBuilder.bind(mempoolQueue).to(mempoolExchange).with(MEMPOOL_QUEUE);
    }

    @Bean
    public Binding bindingEventBusQueue(Queue eventBusQueue, DirectExchange eventBusExchange) {
        return BindingBuilder.bind(eventBusQueue).to(eventBusExchange).with(EVENT_BUS_QUEUE);
    }

    // ─── Message Converter ───────────────────────────────────────────────────
    // Serialises Java objects → JSON when sending, and deserialises JSON → Java
    // when receiving. Both sides (Backend and blockchain nodes) must agree on
    // the same JSON field names — that is the only "API contract" between them.
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ─── RabbitTemplate ──────────────────────────────────────────────────────
    // The main Spring tool used to SEND messages programmatically.
    // ConnectionFactory is injected automatically by Spring Boot — it is built
    // from the spring.rabbitmq.* values in application.properties.
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
