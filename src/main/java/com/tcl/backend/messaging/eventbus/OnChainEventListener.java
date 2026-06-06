package com.tcl.backend.messaging.eventbus;

import com.tcl.backend.config.RabbitConfig;
import com.tcl.backend.model.InboundEventBusMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// Listens to the event_bus_queue for outcome events pushed by blockchain nodes.
// It delegates all business logic to OnChainEventProcessor — keeping messaging
// infrastructure separate from service/database concerns.
@Component
public class OnChainEventListener {

    private final OnChainEventProcessor eventProcessor;

    // Spring injects whichever @Component implements OnChainEventProcessor.
    // Currently that is ConsoleLoggerProcessor (for testing).
    // When Khôi writes his real service layer, he replaces ConsoleLoggerProcessor
    // with his own implementation — this class does not change at all.
    public OnChainEventListener(OnChainEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    // @RabbitListener tells Spring to subscribe to this queue automatically on startup.
    // Each message arrives as an InboundEventBusMessage (JSON → Java via Jackson).
    // Spring ACKs the message automatically if this method returns normally.
    // If an exception is thrown, Spring NACKs and the message is requeued.
    @RabbitListener(queues = RabbitConfig.EVENT_BUS_QUEUE)
    public void receiveEvent(InboundEventBusMessage message) {
        System.out.println("[EVENT BUS LISTENER] Received event [" + message.getEventId()
                + "] - Type: " + message.getEventType());

        try {
            eventProcessor.processEvent(
                    message.getEventId(),
                    message.getEventType(),
                    message.getTimestamp(),
                    message.getPayload()
            );
        } catch (Exception e) {
            System.err.println("[EVENT BUS LISTENER] Failed to process event "
                    + message.getEventId() + ": " + e.getMessage());
            // Re-throwing causes Spring to NACK → RabbitMQ requeues the message.
            throw e;
        }
    }
}