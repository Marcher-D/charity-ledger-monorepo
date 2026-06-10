package com.tcl.backend.messaging;

/*
 * On-chain identity of one blockchain node, returned by the NodeOrchestrator
 * when a node is spawned for a user or a campaign.
 *
 * - nodeIndex: the orchestrator-assigned index; also selects the node's
 *   signing-service hostname (node-<index>) and P2P port (9000+index).
 * - address: 40-char hex on-chain address (stored as wallet_address).
 * - publicKey: Base64 Ed25519 public key (used as sender/receiver in
 *   on-chain transactions).
 */
public record NodeIdentity(
        int nodeIndex,
        String address,
        String publicKey
) {
}
