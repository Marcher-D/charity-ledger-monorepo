package com.tcl.backend.messaging;

// JSON response returned by POST /internal/v1/transactions/sign.
public record OnChainSignResponse(
        String publicKey,
        String signature,
        String txHash,
        String algorithm,
        Long signedAt
) {
}
