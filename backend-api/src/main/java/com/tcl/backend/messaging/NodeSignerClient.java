package com.tcl.backend.messaging;

import com.tcl.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/*
 * Client for the per-node signing services (network.NodeSignerService in the
 * on-chain repo, one per node container at http://node-<index>:7100).
 *
 * Every transaction must be signed by its own sender. The sender's node holds
 * the private key; this client only ships the canonical transaction fields to
 * that node and receives txId + signature back. The node echoes the fields it
 * actually signed, and the outbound RabbitMQ message is built from that echo.
 */
@Service
public class NodeSignerClient {

    private final OnChainProperties properties;

    public NodeSignerClient(OnChainProperties properties) {
        this.properties = properties;
    }

    // A node container needs a few seconds after spawn before its signing
    // service accepts connections (JVM boot). Retry briefly so a user who
    // registers and immediately transacts doesn't get a spurious 503.
    private static final int MAX_ATTEMPTS = 4;
    private static final long RETRY_DELAY_MS = 2_000;

    public NodeSignResponse sign(int senderNodeIndex, NodeSignRequest request) {
        RestClient restClient = OnChainRestClientFactory.create(
                properties.signerBaseUrl(senderNodeIndex), properties.timeoutMs());
        NodeSignResponse response = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                response = restClient.post()
                        .uri("/api/signer/sign")
                        .body(request)
                        .retrieve()
                        .body(NodeSignResponse.class);
                break;
            } catch (RestClientException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Signing service of node " + senderNodeIndex + " is unavailable");
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Signing service of node " + senderNodeIndex + " is unavailable");
                }
            }
        }

        if (response == null || isBlank(response.txId()) || isBlank(response.ed25519Signature())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Signing service of node " + senderNodeIndex + " returned invalid signature data");
        }
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
