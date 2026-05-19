package com.tcl.backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tcl.backend.transactions.TransactionCache;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;


@Data 
@Builder
@NoArgsConstructor 
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Important for distributed systems, if other people add a new field 
// to network transaction layer, => tell Jackson parser: do not need to throw exception

public class OutboundTransactionMessage {
    private String txId;
    private long nonce_order;
    private String type;
    private String senderAddress;
    private String targetAddress;
    private BigDecimal amount;
    private long timestamp;
    private TransactionCache.Payload payload;
    private String publicKey;
    private String signature;
    private String txHash;
}
