package com.tcl.backend.messaging.mempool;

import java.security.PrivateKey;
import java.security.PublicKey;

import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.model.OutboundTransactionMessage;



public class CryptoTransactionSigner implements TransactionSigner {
    public OutboundTransactionMessage sign(TransactionCache transaction, long nonceOrder, String senderAddress, String targetAddress, PrivateKey privateKey, PublicKey publicKey) {
        
        // Combine all the contents of the data into a String
        String canonicalString = transaction.getTxId() + nonceOrder + transaction.getType() 
         + transaction.getSenderAddress() + transaction.getTargetAddress() +
        transaction.getAmount() + transaction.getTimestamp() + transaction.getPayload();
        // tx Hash
        String txHash = CryptoCore.applySha256(canonicalString);
        // signature
        String signature = CryptoCore.signData(privateKey, canonicalString);
        // public key
        String encodedPublicKey = CryptoCore.encodePublicKey(publicKey);
        // 1. Inserted the calculated hash to Khoi tracking entity: TransactionCache
        transaction.setTxHash(txHash);
        // 2. Return SignedTransaction
        return new OutboundTransactionMessage(transaction.getTxId(), nonceOrder, 
        transaction.getType(), senderAddress, targetAddress, transaction.getAmount(), transaction.getTimestamp(), 
        transaction.getPayload(), encodedPublicKey, signature, txHash);
    }
}
