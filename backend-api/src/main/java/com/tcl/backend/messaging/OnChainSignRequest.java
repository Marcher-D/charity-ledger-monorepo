package com.tcl.backend.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcl.backend.transactions.TransactionType;
import java.util.Map;
import java.util.UUID;

// JSON body sent to POST /internal/v1/transactions/sign.
public record OnChainSignRequest(
        UUID txId,
        @JsonProperty("nonce_order")
        long nonceOrder,
        TransactionType type,
        String senderAddress,
        String targetAddress,
        String amount,
        long timestamp,
        Map<String, Object> payload
) {
}
