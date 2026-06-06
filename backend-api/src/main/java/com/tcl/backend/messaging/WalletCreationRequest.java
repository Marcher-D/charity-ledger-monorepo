package com.tcl.backend.messaging;

import java.util.UUID;

// JSON body sent to POST /internal/v1/wallets.
public record WalletCreationRequest(
        OnChainOwnerType ownerType,
        UUID ownerId
) {
}
