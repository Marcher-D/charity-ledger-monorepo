package com.tcl.backend.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;

/*
 * The exact transaction JSON the blockchain consumes. This is the message our
 * backend publishes to RabbitMQ's mempool_queue; the mock mempool forwards it
 * unchanged to the nodes, which parse it with Gson into model.Transaction.
 *
 * Every field except txId/ed25519Signature is covered by the on-chain canonical
 * signable string:
 *   type|senderPubKey|receiverPubKey|amount(%.2f)|firstValid|lastValid|timestamp|note
 * txId = SHA-256(signable) and ed25519Signature = Ed25519(signable) — both are
 * produced by the sender's signer (per-node service or the system key signer),
 * so the published values here MUST be exactly the ones the signer echoed back.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OnChainTransactionMessage(
        // SHA-256 hex of the canonical signable data; stored as txHash off-chain.
        String txId,
        // On-chain type: STAKE, UNSTAKE, DEPOSIT, WITHDRAW, DONATE.
        String type,
        // Base64 Ed25519 public key of the sender (user node / system key).
        String senderPubKey,
        // Base64 Ed25519 public key of the receiver; null for STAKE/UNSTAKE.
        String receiverPubKey,
        // Token amount; the chain works in doubles with 2-decimal canonical form.
        double amount,
        // Earliest block round at which this transaction is valid.
        long firstValid,
        // Latest block round; after this the chain rejects it with NETWORK_TIMEOUT.
        long lastValid,
        // Unix epoch milliseconds at signing time.
        long timestamp,
        // Optional memo (covered by the signature).
        String note,
        // Base64 Ed25519 signature over the canonical signable data.
        String ed25519Signature
) {
}
