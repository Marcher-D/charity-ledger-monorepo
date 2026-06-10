package com.tcl.backend.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// JSON response of GET /api/orchestrator/health on the NodeOrchestrator.
// chainEpoch increments every time the orchestrator wipes/restarts the chain.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrchestratorHealth(
        String status,
        int nextNodeIndex,
        long chainEpoch
) {
}
