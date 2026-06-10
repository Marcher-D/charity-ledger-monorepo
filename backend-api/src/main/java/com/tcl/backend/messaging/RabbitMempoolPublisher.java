package com.tcl.backend.messaging;

import com.tcl.backend.transactions.TransactionCache;
import java.time.Instant;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/*
 * Concrete implementation of MempoolPublisher that sends transactions to RabbitMQ.
 *
 * Flow for user-signed transactions:
 * 1. Ship canonical fields to the sender node's signing service (NodeSignerClient).
 * 2. The node signs with its own private key and echoes txId + signature + fields.
 * 3. Build OnChainTransactionMessage from the ECHOED values (they are what the
 *    signature covers) and publish it to mempool_exchange -> mempool_queue.
 *
 * Flow for system-signed transactions (DEPOSIT) is the same, but SystemKeySigner
 * builds and signs the message locally with the chain's system key.
 *
 * Because RabbitConfig registered Jackson2JsonMessageConverter, RabbitTemplate
 * converts OnChainTransactionMessage into the exact JSON the blockchain parses
 * with Gson on the node side.
 */
@Component
public class RabbitMempoolPublisher implements MempoolPublisher {

    // The chain rejects transactions whose lastValid round has passed. Rounds
    // advance every few seconds; this window effectively means "no expiry".
    private static final long FIRST_VALID = 0L;
    private static final long LAST_VALID = 999_999_999L;

    // Spring AMQP helper used to send messages to RabbitMQ.
    private final RabbitTemplate rabbitTemplate;
    // Reads queue names from application.yml through RabbitQueueProperties.
    private final RabbitQueueProperties properties;
    // Asks the sender node's signing service for txId + signature.
    private final NodeSignerClient nodeSignerClient;
    // Signs system (DEPOSIT) transactions with the derivable system key.
    private final SystemKeySigner systemKeySigner;

    public RabbitMempoolPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitQueueProperties properties,
            NodeSignerClient nodeSignerClient,
            SystemKeySigner systemKeySigner
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.nodeSignerClient = nodeSignerClient;
        this.systemKeySigner = systemKeySigner;
    }

    @Override
    public void publishUserSigned(TransactionCache transaction, int senderNodeIndex, String receiverPubKey) {
        NodeSignRequest request = new NodeSignRequest(
                transaction.getType().name(),
                receiverPubKey,
                transaction.getAmount().doubleValue(),
                FIRST_VALID,
                LAST_VALID,
                Instant.now().toEpochMilli(),
                transaction.getNote()
        );

        NodeSignResponse signed = nodeSignerClient.sign(senderNodeIndex, request);

        // Build the outbound message from the echoed fields — these are exactly
        // what the node hashed and signed.
        OnChainTransactionMessage message = new OnChainTransactionMessage(
                signed.txId(),
                signed.type(),
                signed.senderPubKey(),
                signed.receiverPubKey(),
                signed.amount(),
                signed.firstValid(),
                signed.lastValid(),
                signed.timestamp(),
                signed.note(),
                signed.ed25519Signature()
        );

        send(transaction, message);
    }

    @Override
    public void publishSystemSigned(TransactionCache transaction, String receiverPubKey) {
        OnChainTransactionMessage message = systemKeySigner.signSystemTransaction(
                transaction.getType().name(),
                receiverPubKey,
                transaction.getAmount(),
                FIRST_VALID,
                LAST_VALID,
                Instant.now().toEpochMilli(),
                transaction.getNote()
        );

        send(transaction, message);
    }

    @Override
    public void publishPrebuilt(OnChainTransactionMessage message) {
        rabbitTemplate.convertAndSend(properties.mempoolExchange(), properties.mempoolQueue(), message);
    }

    private void send(TransactionCache transaction, OnChainTransactionMessage message) {
        /*
         * The on-chain txId is the correlation key: TXN_VALIDATED/TXN_REJECTED
         * events reference it, and OnChainEventService looks the row up by txHash.
         * The caller saves the transaction again after publish returns.
         */
        transaction.setTxHash(message.txId());

        /*
         * RabbitTemplate.convertAndSend(exchange, routingKey, object):
         * - converts the Java object into a JSON message body
         * - sends it to mempool_exchange
         * - RabbitMQ routes it into mempool_queue through RabbitConfig's binding
         * The mock mempool consumes it and serves it to blockchain nodes over HTTP.
         */
        rabbitTemplate.convertAndSend(properties.mempoolExchange(), properties.mempoolQueue(), message);
    }
}
