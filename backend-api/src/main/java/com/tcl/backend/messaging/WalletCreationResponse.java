package com.tcl.backend.messaging;

// JSON response returned by POST /internal/v1/wallets.
public record WalletCreationResponse(
        String walletAddress,
        String publicKey,
        String keyId,
        String algorithm
) {
}
