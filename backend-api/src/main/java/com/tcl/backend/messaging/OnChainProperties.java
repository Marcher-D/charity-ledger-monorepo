package com.tcl.backend.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds app.onchain values from application.yml into a typed Java object.
@ConfigurationProperties(prefix = "app.onchain")
public record OnChainProperties(
        // Base URL for the internal on-chain wallet/signing service.
        String walletBaseUrl,
        // Shared secret sent as X-Internal-Api-Key on every internal call.
        String internalApiKey,
        // HTTP connect/read timeout in milliseconds.
        int timeoutMs
) {
}
