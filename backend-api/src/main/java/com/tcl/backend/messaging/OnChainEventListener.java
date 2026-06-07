package com.tcl.backend.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/*
 * Background RabbitMQ listener for on-chain results.
 *
 * This class is not called by a REST controller. It runs in the background after
 * Spring Boot starts. When a message arrives in event_bus_queue, Spring AMQP calls
 * the consume(...) method automatically.
 *
 * Flow:
 * 1. Backend previously publishes a transaction to mempool_queue.
 * 2. On-chain/blockchain side processes the transaction.
 * 3. Event Bus sends a result message to event_bus_queue.
 * 4. This listener receives that message.
 * 5. OnChainEventService updates PostgreSQL.
 */
@Component
public class OnChainEventListener {

    // Service that contains the real business logic for applying on-chain results.
    private final OnChainEventService onChainEventService;

    // Constructor injection: Spring gives this listener the OnChainEventService bean.
    public OnChainEventListener(OnChainEventService onChainEventService) {
        this.onChainEventService = onChainEventService;
    }

    /*
     * @RabbitListener tells Spring AMQP:
     * "Call this method whenever a message arrives from this RabbitMQ queue."
     *
     * queues = "${app.rabbitmq.event-bus-queue}" means:
     * read the queue name from application.yml, not hard-code it here.
     *
     * Spring also converts the incoming JSON into EventBusMessage before calling
     * this method, using the Jackson message converter from RabbitConfig.
     */
    @RabbitListener(queues = "${app.rabbitmq.event-bus-queue}")
    public void consume(EventBusMessage message) {
        // Keep listener thin: receive the message, then delegate logic to the service.
        onChainEventService.apply(message);
    }
}
