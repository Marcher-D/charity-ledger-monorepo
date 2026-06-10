package com.tcl.backend.messaging.mempool;

import com.tcl.backend.config.RabbitConfig;
import com.tcl.backend.model.OutboundTransactionMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

// Responsible for ONE thing only: publishing a transaction onto the mempool queue.
// Nonce ordering and batch assembly are the responsibility of the blockchain coordinator (on-chain side).
@Service
public class RabbitMempoolPublisher {

    private final RabbitTemplate rabbitTemplate;

    // Spring sees this single-argument constructor and automatically
    // injects the RabbitTemplate bean defined in RabbitConfig.
    public RabbitMempoolPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Sends one transaction to the mempool exchange.
     * RabbitMQ routes it to mempool_queue via the Direct binding defined in RabbitConfig.
     *
     * @param tx the transaction to publish
     */
    public void publish(OutboundTransactionMessage tx) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.MEMPOOL_EXCHANGE, // which exchange to send to
                RabbitConfig.MEMPOOL_QUEUE,    // routing key — must match the binding key
                tx                             // Java object → serialised to JSON automatically
        );
        System.out.println("[MEMPOOL PUBLISHER] Transaction published: " + tx.getTxId());
    }
}