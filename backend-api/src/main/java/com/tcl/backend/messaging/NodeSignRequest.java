package com.tcl.backend.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;

// Request body for POST /api/signer/sign on a node's signing service.
// The signer adds its own senderPubKey, computes txId and signs.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NodeSignRequest(
        String type,
        String receiverPubKey,
        double amount,
        long firstValid,
        long lastValid,
        long timestamp,
        String note
) {
}
