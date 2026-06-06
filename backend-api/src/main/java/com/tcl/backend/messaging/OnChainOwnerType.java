package com.tcl.backend.messaging;

// Metadata sent to the on-chain wallet API so it can separate user wallets
// from campaign wallets without guessing from UUID values alone.
public enum OnChainOwnerType {
    USER,
    CAMPAIGN
}
