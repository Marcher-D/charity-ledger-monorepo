package com.tcl.backend.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds app.rabbitmq values from application.yml into a typed Java object.
// Queue and exchange names are read from config so they can be changed
// via environment variables without recompiling the code.
@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitQueueProperties(
        // Maps to app.rabbitmq.mempool-exchange in application.yml.
        // Exchange that routes outbound transactions into mempool_queue.
        String mempoolExchange,
        // Maps to app.rabbitmq.mempool-queue in application.yml.
        // Queue where backend publishes signed transactions for blockchain nodes to consume.
        String mempoolQueue,
        // Maps to app.rabbitmq.event-bus-exchange in application.yml.
        // Exchange that routes on-chain results into event_bus_queue.
        String eventBusExchange,
        // Maps to app.rabbitmq.event-bus-queue in application.yml.
        // Queue where blockchain nodes publish block/transaction results back to backend.
        String eventBusQueue
) {
}
