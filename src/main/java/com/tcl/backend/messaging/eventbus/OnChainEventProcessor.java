package com.tcl.backend.messaging.eventbus;

import java.util.Map;

/**
 * Interface contract for Khôi to implement in his Service Layer.
 * Decouples RabbitMQ infrastructure from PostgreSQL business logic.
 */
public interface OnChainEventProcessor {
    
    /**
     * Processes execution events returning from the blockchain nodes.
     * * @param eventId   Idempotency key to prevent duplicate processing[cite: 2158].
     * @param eventType Type of event (e.g., BLOCK_SUCCESS, TX_FAIL)[cite: 2152].
     * @param timestamp Unix execution time[cite: 2160].
     * @param payload   Dynamic payload data (e.g., failed txIds, reasons)[cite: 2161].
     */
    void processEvent(String eventId, String eventType, long timestamp, Map<String, Object> payload);
}