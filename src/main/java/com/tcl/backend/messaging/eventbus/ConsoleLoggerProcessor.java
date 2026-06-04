package com.tcl.backend.messaging.eventbus;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class ConsoleLoggerProcessor implements OnChainEventProcessor {

    @Override
    public void processEvent(String eventId, String eventType, long timestamp, Map<String, Object> payload) {
        // In ra màn hình terminal một cách rõ ràng khi nhận được sự kiện
        System.out.println("\n========================================================");
        System.out.println("[EVENT BUS RECEIVED] Phát hiện sự kiện mới từ On-chain!");
        System.out.println(">> Timestamp: " + timestamp);
        System.out.println(">> Loại sự kiện: " + eventType);
        System.out.println(">> Dữ liệu chi tiết: " + payload);
        System.out.println("========================================================\n");
    }
}