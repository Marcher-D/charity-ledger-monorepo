package com.tcl.backend.messaging;

import com.tcl.backend.common.ApiException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Real WalletService implementation backed by the on-chain wallet REST API.
// Private keys stay inside the on-chain service; this backend only receives addresses.
@Service
public class OnChainWalletService implements WalletService {

    private final RestClient restClient;

    @Autowired
    public OnChainWalletService(OnChainProperties properties) {
        this.restClient = OnChainRestClientFactory.create(properties);
    }

    OnChainWalletService(OnChainProperties properties, RestClient.Builder builder) {
        this.restClient = OnChainRestClientFactory.create(properties, builder);
    }

    OnChainWalletService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String generateUserWalletAddress(UUID ownerId) {
        return createWallet(OnChainOwnerType.USER, ownerId);
    }

    @Override
    public String generateCampaignWalletAddress(UUID ownerId) {
        return createWallet(OnChainOwnerType.CAMPAIGN, ownerId);
    }

    private String createWallet(OnChainOwnerType ownerType, UUID ownerId) {
        WalletCreationResponse response;
        try {
            response = restClient.post()
                    .uri("/internal/v1/wallets")
                    .body(new WalletCreationRequest(ownerType, ownerId))
                    .retrieve()
                    .body(WalletCreationResponse.class);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "On-chain wallet service is unavailable");
        }

        if (response == null || isBlank(response.walletAddress())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "On-chain wallet service returned invalid wallet data");
        }
        return response.walletAddress();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
