package com.tcl.backend.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Response body of POST /api/signer/sign on a node's signing service.
// Echoes the canonical fields the node actually signed; the outbound RabbitMQ
// message must be built from these echoed values, never from local copies.
@JsonIgnoreProperties(ignoreUnknown = true)
public record NodeSignResponse(
        String txId,
        String senderPubKey,
        String address,
        String ed25519Signature,
        String type,
        String receiverPubKey,
        double amount,
        long firstValid,
        long lastValid,
        long timestamp,
        String note
) {
}
