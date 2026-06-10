package com.tcl.backend.messaging;

import com.tcl.backend.transactions.TransactionCache;

/*
 * Publishes on-chain transactions to the RabbitMQ mempool.
 *
 * Both methods sign the transaction (with the sender's own node, or the system
 * key), set transaction.txHash to the on-chain txId, and publish the canonical
 * transaction JSON to mempool_queue. The caller saves the TransactionCache row
 * again after publish returns so the txHash lands in PostgreSQL.
 */
public interface MempoolPublisher {

    // User/campaign-sent transactions (DONATE, STAKE, UNSTAKE, WITHDRAW):
    // signed by the sender node's signing service.
    void publishUserSigned(TransactionCache transaction, int senderNodeIndex, String receiverPubKey);

    // System-sent transactions (DEPOSIT: system -> user): signed locally with
    // the derivable SYSTEM_DEPOSIT_KEY_V1 key.
    void publishSystemSigned(TransactionCache transaction, String receiverPubKey);

    // Publishes an already-signed transaction without touching any
    // TransactionCache row. Used by ChainStateReconciler to replay balances
    // onto a freshly reset chain: the resulting TXN_VALIDATED events match no
    // cached row and are deliberately ignored off-chain.
    void publishPrebuilt(OnChainTransactionMessage message);
}
