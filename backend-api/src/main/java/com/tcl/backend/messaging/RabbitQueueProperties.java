package com.tcl.backend.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds app.rabbitmq values from application.yml into a typed Java object.
@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitQueueProperties(
        // Maps to app.rabbitmq.mempool-exchange in application.yml.
        String mempoolExchange,
        // Maps to app.rabbitmq.mempool-queue in application.yml.
        String mempoolQueue,
        // Maps to app.rabbitmq.mempool-dlq in application.yml.
        String mempoolDlq,
        // Maps to app.rabbitmq.event-bus-queue in application.yml.
        String eventBusQueue,
        // Maps to app.rabbitmq.event-bus-dlq in application.yml.
        String eventBusDlq
) {
}
