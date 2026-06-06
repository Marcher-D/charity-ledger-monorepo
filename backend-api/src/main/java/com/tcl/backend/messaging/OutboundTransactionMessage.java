package com.tcl.backend.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcl.backend.transactions.TransactionType;
import java.util.Map;
import java.util.UUID;

/*
 * This record is the exact JSON message our backend sends to RabbitMQ's
 * mempool_queue.
 *
 * It is not a database entity. It is an outbound DTO/message contract:
 * - the frontend calls our REST API
 * - the backend creates a TransactionCache row
 * - the backend builds this message
 * - RabbitMempoolPublisher sends this message to RabbitMQ
 * - the on-chain/mempool side reads this JSON
 *
 * Jackson is the JSON library Spring Boot uses here. Jackson converts this
 * Java record into JSON keys and values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboundTransactionMessage(
        /*
         * txId is our backend transaction UUID from transactions_cache.id.
         * It lets the Event Bus later tell us which cached transaction succeeded
         * or failed.
         */
        UUID txId,
        /*
         * Java field name: nonceOrder
         * JSON key name: nonce_order
         *
         * @JsonProperty tells Jackson to use a different JSON field name.
         * Without this annotation, Jackson would output "nonceOrder".
         *
         * The project lead specifically requested "nonce_order" after txId and
         * before type in the outbound JSON. This annotation gives that snake_case
         * JSON name while keeping normal camelCase Java naming.
         */
        @JsonProperty("nonce_order")
        long nonceOrder,
        // Transaction action: CREATE, DONATE, STAKE, UNSTAKE, VALIDATOR_TOGGLE, etc.
        TransactionType type,
        // Wallet/on-chain address of the account that starts the transaction.
        String senderAddress,
        // Wallet/on-chain address or logical target that receives the transaction.
        String targetAddress,
        // Money is sent as a string so JSON keeps the exact decimal value.
        String amount,
        // Time the backend created the outbound message, usually epoch milliseconds.
        long timestamp,
        /*
         * Extra transaction-specific data.
         *
         * Map<String, Object> means:
         * - keys are strings, like "campaignId" or "note"
         * - values can be different JSON-compatible types, like String, number,
         *   boolean, or null
         *
         * This keeps the main message stable while allowing CREATE/DONATE/STAKE
         * to include different details.
         */
        Map<String, Object> payload,
        // Public key used by the on-chain side to verify the signature.
        String publicKey,
        // Signature proving approval of this transaction data. Stubbed for now.
        String signature,
        // Hash/fingerprint of the transaction message data.
        String txHash
) {
}
