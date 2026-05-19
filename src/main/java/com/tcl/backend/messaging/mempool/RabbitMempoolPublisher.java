package com.tcl.backend.messaging.mempool;

import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.config.RabbitConfig;
import com.tcl.backend.model.OutboundTransactionMessage;
// import com.tcl.backend.messaging.RabbitConfig; // Import your config to use its constants
import org.springframework.amqp.rabbit.core.RabbitTemplate; // Import the proper template
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;

@Service
public class RabbitMempoolPublisher {
    private final String mempoolQueue = RabbitConfig.MEMPOOL_QUEUE;
    private final NonceService nonceService;
    private final CryptoTransactionSigner transactionSigner;
    private final RabbitTemplate rabbitTemplate;
    
    public RabbitMempoolPublisher(NonceService nonceService, 
                                  CryptoTransactionSigner transactionsSigner, 
                                  RabbitTemplate rabbitTemplate) {
        this.nonceService = nonceService;
        this.transactionSigner = transactionsSigner;
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void publish(TransactionCache transaction, PrivateKey privateKey, PublicKey publicKey) {
        long nonceOrder = nonceService.nextNonce();
        OutboundTransactionMessage networkMessage = transactionSigner.sign(
            transaction, nonceOrder, transaction.getSenderAddress(),
            transaction.getTargetAddress(), privateKey, publicKey);
        
            // Use the injected template tool! 
        // Behind the scenes, it uses the Jackson converter from your RabbitConfig to auto-stringify the object to JSON.
        rabbitTemplate.convertAndSend("", mempoolQueue, networkMessage);
    }    
}
