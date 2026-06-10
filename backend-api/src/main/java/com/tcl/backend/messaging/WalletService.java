package com.tcl.backend.messaging;

import java.util.UUID;

/*
 * Provisions an on-chain identity for an owner (user or campaign).
 *
 * The real implementation asks the NodeOrchestrator to spawn a dedicated
 * blockchain node; the returned NodeIdentity carries the node's index,
 * on-chain address and Ed25519 public key. The node holds its own private
 * key — this backend never receives it.
 */
public interface WalletService {

    NodeIdentity provisionUserNode(UUID ownerId);

    NodeIdentity provisionCampaignNode(UUID ownerId);
}
