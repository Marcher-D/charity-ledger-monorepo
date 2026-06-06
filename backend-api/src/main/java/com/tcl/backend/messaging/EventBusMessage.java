package com.tcl.backend.messaging;

import java.util.Map;
import java.util.UUID;

public record EventBusMessage(
        UUID eventId,
        EventBusEventType eventType,
        long timestamp,
        Map<String, Object> payload
) {
}
