package com.tcl.backend.model;

import java.math.BigDecimal;
import java.util.Map; 

public class OutboundTransactionMessage {
    private String txId;
    private long nonceOrder;

    private String type;
    private String senderAddress;
    private String targetAddress;
    private BigDecimal amount;
    private Long timestamp;
    private Map<String, Object> payload;
    private String publicKey;
    private String signature;

    public OutboundTransactionMessage() {}

    // Getters & Setters
    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public Long getNonceOrder() { return nonceOrder; }
    public void setNonceOrder(Long nonceOrder) { this.nonceOrder = nonceOrder; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String senderAddress) { this.senderAddress = senderAddress; }

    public String getTargetAddress() { return targetAddress; }
    public void setTargetAddress(String targetAddress) { this.targetAddress = targetAddress; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

}