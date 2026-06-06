package com.tcl.backend.transactions;

import java.util.UUID;

// Response DTO returned immediately after submitting a transaction action.
// Example use: donate, stake, unstake, validator toggle.
// It tells frontend the action was accepted and is waiting for on-chain confirmation.
public record TransactionActionResponse(
        UUID txId,
        String txHash,
        TransactionStatus status,
        String message
) {
}
