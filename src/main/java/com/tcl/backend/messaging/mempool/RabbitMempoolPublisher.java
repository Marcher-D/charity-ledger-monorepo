package com.tcl.backend.messaging.mempool;

import com.tcl.backend.model.OutboundTransactionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMempoolPublisher {

    private final RabbitTemplate rabbitTemplate;
    
    // Tracks the NEXT expected nonce for each sender (Wallet Address)
    private final Map<String, Long> expectedNonces = new ConcurrentHashMap<>();
    
    // The Resequencer Buffer: Holds early transactions sorted by Nonce
    private final Map<String, PriorityQueue<OutboundTransactionMessage>> buffer = new ConcurrentHashMap<>();
    
    // Concurrency Locks: Ensures thread-safety for high-speed incoming requests per sender
    private final Map<String, Object> senderLocks = new ConcurrentHashMap<>();

    /**
     * Publishes a transaction to the mempool, buffering it if it arrives out of order.
     */
    public void publish(OutboundTransactionMessage tx) {
        String sender = tx.getSenderAddress();
        long currentNonce = tx.getNonceOrder();

        // Initialize expectation (Assuming nonce starts at 1. Adjust if your logic starts at 0)
        expectedNonces.putIfAbsent(sender, 1L);
        Object lock = senderLocks.computeIfAbsent(sender, k -> new Object());

        // Synchronize on the specific sender to prevent race conditions during rapid spam
        synchronized (lock) {
            long expected = expectedNonces.get(sender);

            if (currentNonce == expected) {
                // Perfect order: Send immediately
                sendToBroker(tx);
                expectedNonces.put(sender, expected + 1);
                
                // Recursively check if the next expected nonces are already waiting in the buffer
                processBufferedTransactions(sender);
                
            } else if (currentNonce > expected) {
                // Out of order (Arrived too early): Put it in the priority queue buffer
                log.warn("Out of order TX for {}. Expected {}, got {}. Buffering.", sender, expected, currentNonce);
                
                buffer.computeIfAbsent(sender, k -> new PriorityQueue<>(
                        Comparator.comparingLong(OutboundTransactionMessage::getNonceOrder)
                )).add(tx);
                
            } else {
                // Arrived too late or duplicated
                log.error("Stale TX detected for {}. Expected {}, got {}. Dropping.", sender, expected, currentNonce);
            }
        }
    }

    /**
     * Drains the buffer for a sender if the newly expected nonces are available.
     */
    private void processBufferedTransactions(String sender) {
        PriorityQueue<OutboundTransactionMessage> pq = buffer.get(sender);
        if (pq == null) return;

        // Keep sending as long as the item at the front of the queue matches the NEXT expected nonce
        while (!pq.isEmpty() && pq.peek().getNonceOrder() == expectedNonces.get(sender)) {
            OutboundTransactionMessage nextTx = pq.poll();
            sendToBroker(nextTx);
            expectedNonces.put(sender, expectedNonces.get(sender) + 1);
        }
    }

    private void sendToBroker(OutboundTransactionMessage tx) {
        // Broadcasts to the specific exchange mapped in RabbitConfig
        rabbitTemplate.convertAndSend("mempool_exchange", "mempool_queue", tx);
        log.info("Successfully pushed TX {} (Nonce: {}) to Mempool.", tx.getTxId(), tx.getNonceOrder());
    }
}