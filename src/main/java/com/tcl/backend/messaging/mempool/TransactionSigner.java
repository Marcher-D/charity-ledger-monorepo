package com.tcl.backend.messaging.mempool;

import com.tcl.backend.model.OutboundTransactionMessage;
import com.tcl.backend.transactions.TransactionCache;
import java.security.PrivateKey;
import java.security.PublicKey;


public interface TransactionSigner {    
    public OutboundTransactionMessage sign(
        TransactionCache transaction, long nonceOrder, 
        String senderAddress, String targetAddress, 
        PrivateKey privateKey, PublicKey publicKey);
}
