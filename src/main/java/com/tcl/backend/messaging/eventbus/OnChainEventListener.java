package com.tcl.backend.messaging.eventbus;

import com.tcl.backend.model.InboundEventBusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnChainEventListener {

    // Injects the implementation(s) provided by Khôi (or your ConsoleLogger during testing)
    private final OnChainEventProcessor eventProcessor;

    @RabbitListener(queues = "event_bus_queue")
    public void receiveEvent(InboundEventBusMessage message) {
        log.info("Received Event [{}] - Type: {}", message.getEventId(), message.getEventType());

        try {
            // message.getPayload() is already type-safe Map<String, Object>, no cast needed! [30]
            eventProcessor.processEvent(
                    message.getEventId(),
                    message.getEventType(),
                    message.getTimestamp(),
                    message.getPayload()
            );
            
        } catch (Exception e) {
            log.error("Critical failure processing event {}: {}", message.getEventId(), e.getMessage());
            // Re-throwing the exception ensures RabbitMQ pushes this failed message to the DLQ [24, 30]
            throw e; 
        }
    }
}