package com.tcl.backend.messaging;

import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.transactions.TransactionCache;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/*
 * Concrete implementation of MempoolPublisher that sends transactions to RabbitMQ.
 *
 * MempoolPublisher is only an interface. This class is the real Spring bean that
 * knows how to publish to RabbitMQ using Spring AMQP's RabbitTemplate.
 *
 * RabbitTemplate is a helper object provided by Spring AMQP. It hides the low-level
 * RabbitMQ client details. Instead of manually opening a connection/channel and
 * serializing JSON, we call rabbitTemplate.convertAndSend(...).
 *
     * Because RabbitConfig registered Jackson2JsonMessageConverter, RabbitTemplate can
     * convert OutboundTransactionMessage into JSON before sending it through the
     * mempool exchange owned by the RabbitMQ/on-off-chain bridge.
 */
@Component
public class RabbitMempoolPublisher implements MempoolPublisher {

    // Spring AMQP helper used to send messages to RabbitMQ.
    private final RabbitTemplate rabbitTemplate;
    // Reads queue names from application.yml through RabbitQueueProperties.
    private final RabbitQueueProperties properties;
    // Builds txHash/signature/publicKey through the internal on-chain signing API.
    private final TransactionSigner transactionSigner;
    // Generates the global nonce_order requested by the project lead.
    private final NonceService nonceService;
    // Formats BigDecimal money into canonical string form before JSON publish.
    private final MoneyFormatter moneyFormatter;

    // Constructor injection: Spring provides these beans when it creates RabbitMempoolPublisher.
    public RabbitMempoolPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitQueueProperties properties,
            TransactionSigner transactionSigner,
            NonceService nonceService,
            MoneyFormatter moneyFormatter
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.transactionSigner = transactionSigner;
        this.nonceService = nonceService;
        this.moneyFormatter = moneyFormatter;
    }

    @Override
    public void publish(TransactionCache transaction, String senderAddress, String targetAddress) {
        // Generate nonce_order only for the outbound JSON. We do not store it in PostgreSQL.
        long nonceOrder = nonceService.nextNonceOrder();
        /*
         * LinkedHashMap keeps insertion order.
         *
         * A normal HashMap does not promise order. LinkedHashMap makes the payload JSON
         * appear in the same order we put values here, which is easier to read/debug.
         */
        Map<String, Object> payload = new LinkedHashMap<>();
        // campaignId is null for transaction types that are not tied to a campaign.
        payload.put("campaignId", transaction.getCampaign() == null ? null : transaction.getCampaign().getId());
        // note carries small transaction-specific text, such as validator action.
        payload.put("note", transaction.getNote());
        // The timestamp must be identical in the signing request and final RabbitMQ message.
        long timestamp = Instant.now().toEpochMilli();

        /*
         * Build the cryptographic fields for this exact outbound transaction data.
         *
         * The signer returns:
         * - publicKey: used by the on-chain side to verify signature
         * - signature: approval/signature over the transaction data
         * - txHash: fingerprint/identifier of the transaction data
         */
        SignedTransaction signed = transactionSigner.sign(transaction, nonceOrder, senderAddress, targetAddress, timestamp, payload);
        /*
         * Save txHash on the TransactionCache object in memory.
         *
         * This method does not call transactionRepository.save(...). The service that
         * called publish(...) saves the transaction again after publish returns.
         */
        transaction.setTxHash(signed.txHash());

        /*
         * Build the outbound DTO that Jackson will convert into JSON.
         *
         * This is the object that gets sent to mempool_queue.
         */
        OutboundTransactionMessage message = new OutboundTransactionMessage(
                transaction.getId(),
                nonceOrder,
                transaction.getType(),
                senderAddress,
                targetAddress,
                moneyFormatter.canonical(transaction.getAmount()),
                timestamp,
                payload,
                signed.publicKey(),
                signed.signature(),
                signed.txHash()
        );
        /*
         * Send message to RabbitMQ.
         *
         * properties.mempoolExchange() returns "mempool_exchange".
         * properties.mempoolQueue() returns the routing key/queue name, "mempool_queue".
         *
         * RabbitTemplate.convertAndSend(exchange, routingKey, object) means:
         * - convert the Java object into a message body, here JSON
         * - send it to the exchange
         * - RabbitMQ routes it into mempool_queue through RabbitConfig's binding
         */
        rabbitTemplate.convertAndSend(properties.mempoolExchange(), properties.mempoolQueue(), message);
    }
}
