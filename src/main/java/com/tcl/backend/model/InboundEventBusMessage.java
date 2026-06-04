package com.tcl.backend.model;

import java.util.Map;

public class InboundEventBusMessage {
    private String eventId;
    private String eventType; // BLOCK_SUCCESS, BLOCK_FAIL, TX_FAIL, TX_EXPIRED
    private long timestamp;
    private Map<String, Object> payload;

    public InboundEventBusMessage() {}

    public String getEventId() {return eventId;}
    public void setEventId(String eventId) {this.eventId = eventId;}
    
    public String getEventType() {return eventType;}
    public void setEventType(String eventType) {this.eventType = eventType;}

    public long getTimestamp() {return timestamp;}
    public void setTimestamp(long timestamp) {this.timestamp = timestamp;}

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload;}
}