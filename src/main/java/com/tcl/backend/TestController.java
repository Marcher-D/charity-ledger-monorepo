package com.tcl.backend; // Hoặc com.tcl.backend.controller nếu bạn tạo folder riêng

import com.tcl.backend.config.RabbitConfig;
import com.tcl.backend.messaging.mempool.RabbitMempoolPublisher;
import com.tcl.backend.model.InboundEventBusMessage;
import com.tcl.backend.model.OutboundTransactionMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private RabbitMempoolPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * ENDPOINT 1: Mô phỏng người dùng click Gửi tiền quyên góp (Mempool Pipeline).
     * URL: http://localhost:8080/api/test/publish?sender=0xTRAC&nonce=1
     */
    @GetMapping("/publish")
    public ResponseEntity<String> testPublish(
            @RequestParam String sender,
            @RequestParam long nonce) {
        
        // 1. Khởi tạo một gói tin giao dịch mẫu
        OutboundTransactionMessage tx = new OutboundTransactionMessage();
        tx.setTxId(UUID.randomUUID().toString()); // Tạo ID ngẫu nhiên
        tx.setSenderAddress(sender);
        tx.setTargetAddress("0xKHOA_RECEIVER");
        tx.setAmount(new BigDecimal("250.00"));
        tx.setNonceOrder(nonce); // Sử dụng nonce do bạn truyền vào từ URL
        tx.setType("DONATE");
        tx.setTimestamp(System.currentTimeMillis());

        try {
            // 2. Gọi publisher của bạn để xử lý tuần tự và đưa vào RabbitMQ
            publisher.publish(tx);
            return ResponseEntity.ok(String.format(
                "Thành công: Đã gửi Giao dịch của [%s] với Nonce [%d] vào Mempool!", 
                sender, nonce
            ));
        } catch (IllegalArgumentException e) {
            // Trả về lỗi 400 nếu vi phạm trình tự Nonce (ngăn chặn Race Condition)
            return ResponseEntity.badRequest().body("Lỗi logic: " + e.getMessage());
        }
    }

    /**
     * ENDPOINT 2: Giả lập Blockchain Node gửi Sự kiện về lại Web Server (Event Bus Pipeline).
     * URL: http://localhost:8080/api/test/event?eventType=BLOCK_SUCCESS
     */
    @GetMapping("/event")
    public ResponseEntity<String> testEvent(@RequestParam String eventType) {
        // 1. Tạo bản tin sự kiện mẫu
        InboundEventBusMessage event = new InboundEventBusMessage();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setTimestamp(System.currentTimeMillis());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("info", "Mô phỏng sự kiện: " + eventType);
        payload.put("blockIndex", 1542);
        event.setPayload(payload);

        // 2. Bắn trực tiếp vào event_bus_queue để kiểm tra Listener
        rabbitTemplate.convertAndSend("", RabbitConfig.EVENT_BUS_QUEUE, event);
        
        return ResponseEntity.ok("Đã giả lập gửi sự kiện [" + eventType + "] lên Event Bus!");
    }
}