package com.tcl.backend.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
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
 * 1. Backend previously publishes a signed transaction to mempool_queue.
 * 2. Blockchain nodes process it through BFT consensus.
 * 3. The EventGateway on each node publishes the result to event_bus_queue.
 * 4. This listener receives that message.
 * 5. OnChainEventService updates PostgreSQL.
 *
 * The parameter type is the raw AMQP Message on purpose: the on-chain
 * EventGateway publishes Gson-serialized JSON with NO contentType header, so
 * relying on Spring's content-type-based message conversion is fragile. We
 * parse the body ourselves with Jackson.
 */
@Component
public class OnChainEventListener {

    private static final Logger log = LoggerFactory.getLogger(OnChainEventListener.class);

    // Service that contains the real business logic for applying on-chain results.
    private final OnChainEventService onChainEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     */
    @RabbitListener(queues = "${app.rabbitmq.event-bus-queue}")
    public void consume(Message message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        EventBusMessage event;
        try {
            event = parse(json);
        } catch (Exception e) {
            // Malformed/unknown events are logged and dropped, never requeued —
            // requeueing a permanently bad message would loop forever.
            log.warn("Dropping unparseable event bus message: {}", json, e);
            return;
        }
        if (event == null) {
            return;
        }
        onChainEventService.apply(event);
    }

    /*
     * Envelope shape (see offchain_integration_specification.md):
     * { "eventId": "uuid", "eventType": "TXN_VALIDATED|TXN_REJECTED|BLOCK_FINALIZED",
     *   "timestamp": 1718029255000, "payload": { ... } }
     */
    private EventBusMessage parse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        String typeText = root.path("eventType").asText(null);
        EventBusEventType eventType;
        try {
            eventType = EventBusEventType.valueOf(typeText);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Ignoring event with unknown eventType: {}", typeText);
            return null;
        }

        UUID eventId = null;
        String idText = root.path("eventId").asText(null);
        if (idText != null && !idText.isBlank()) {
            eventId = UUID.fromString(idText);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(root.path("payload"), Map.class);

        return new EventBusMessage(eventId, eventType, root.path("timestamp").asLong(), payload);
    }
}
