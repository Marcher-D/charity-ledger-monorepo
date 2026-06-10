package com.tcl.backend.messaging;

import com.tcl.backend.common.ApiException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/*
 * WalletService implementation backed by the NodeOrchestrator.
 *
 * Every user registration and campaign approval spawns one dedicated
 * blockchain node (its own Docker container) through
 * POST /api/orchestrator/spawn_node. The orchestrator derives the node's
 * deterministic Ed25519 identity and returns {nodeIndex, port, publicKey,
 * address}; the private key never leaves the node.
 */
@Service
public class OrchestratorWalletService implements WalletService {

    private final RestClient restClient;

    @Autowired
    public OrchestratorWalletService(OnChainProperties properties) {
        this.restClient = OnChainRestClientFactory.create(properties.orchestratorBaseUrl(), properties.timeoutMs());
    }

    OrchestratorWalletService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public NodeIdentity provisionUserNode(UUID ownerId) {
        return spawnNode();
    }

    @Override
    public NodeIdentity provisionCampaignNode(UUID ownerId) {
        return spawnNode();
    }

    // ownerId is not sent: the orchestrator assigns identities purely by node
    // index. The caller persists the returned identity next to the owner row.
    private NodeIdentity spawnNode() {
        SpawnNodeResponse response;
        try {
            response = restClient.post()
                    .uri("/api/orchestrator/spawn_node")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(SpawnNodeResponse.class);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Node orchestrator is unavailable");
        }

        if (response == null || isBlank(response.address()) || isBlank(response.publicKey())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Node orchestrator returned invalid node identity");
        }
        return new NodeIdentity(response.nodeIndex(), response.address(), response.publicKey());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
