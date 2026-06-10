package com.tcl.backend.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds app.onchain values from application.yml into a typed Java object.
@ConfigurationProperties(prefix = "app.onchain")
public record OnChainProperties(
        // Base URL of the NodeOrchestrator (POST /api/orchestrator/spawn_node).
        String orchestratorBaseUrl,
        // Hostname pattern of per-node signing services; %d is the node index
        // (e.g. "node-%d" resolves node 7's signer at http://node-7:<signerPort>).
        String nodeHostPattern,
        // Port of the per-node signing service inside each node container.
        int signerPort,
        // HTTP connect/read timeout in milliseconds.
        int timeoutMs,
        // Number of genesis validator nodes spawned at startup (the chain funds
        // and stakes exactly the first five identities).
        int genesisNodeCount
) {

    // Builds the signer base URL for one node, e.g. http://node-7:7100
    public String signerBaseUrl(int nodeIndex) {
        String host = nodeHostPattern.contains("%d")
                ? String.format(nodeHostPattern, nodeIndex)
                : nodeHostPattern;
        return "http://" + host + ":" + signerPort;
    }
}
