package com.tcl.backend.messaging;

public record SignedTransaction(
        String publicKey,
        String signature,
        String txHash
) {
}
