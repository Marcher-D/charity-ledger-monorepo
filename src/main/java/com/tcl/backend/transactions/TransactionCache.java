package com.tcl.backend.transactions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCache {
    private String txId;
    private String type;
    private String senderAddress;
    private String targetAddress;
    private BigDecimal amount;
    private long timestamp;
    private Payload payload; // Static nested inner class is now housed here cleanly!
    
    // Khôi's PostgreSQL states (e.g., "PENDING", "SUCCESS", "FAIL")
    private String status; 

    // Your cryptographic signature marker slot
    private String txHash; 

    // The nested metadata carrier
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private String note;
        private String extraData;
    }
}