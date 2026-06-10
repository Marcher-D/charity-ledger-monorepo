package com.tcl.backend.messaging;

// JSON response of POST /api/orchestrator/spawn_node on the NodeOrchestrator.
public record SpawnNodeResponse(
        String status,
        int nodeIndex,
        int port,
        String publicKey,
        String address
) {
}
