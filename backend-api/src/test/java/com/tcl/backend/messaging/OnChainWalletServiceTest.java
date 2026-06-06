package com.tcl.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tcl.backend.common.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OnChainWalletServiceTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OnChainWalletService service = new OnChainWalletService(restClient());

    @Test
    void userWalletRequestUsesOwnerMetadataAndApiKey() {
        UUID ownerId = UUID.randomUUID();
        server.expect(requestTo("http://onchain.test/internal/v1/wallets"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "test-secret"))
                .andExpect(content().json("""
                        {
                          "ownerType": "USER",
                          "ownerId": "%s"
                        }
                        """.formatted(ownerId)))
                .andRespond(withSuccess("""
                        {
                          "walletAddress": "0xabc",
                          "publicKey": "04abc",
                          "keyId": "wallet-key-1",
                          "algorithm": "ECDSA_SECP256K1"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.generateUserWalletAddress(ownerId)).isEqualTo("0xabc");
        server.verify();
    }

    @Test
    void upstreamFailureBecomesServiceUnavailable() {
        UUID ownerId = UUID.randomUUID();
        server.expect(requestTo("http://onchain.test/internal/v1/wallets"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.generateCampaignWalletAddress(ownerId))
                .isInstanceOf(ApiException.class)
                .hasMessage("On-chain wallet service is unavailable");
    }

    private RestClient restClient() {
        return builder
                .baseUrl("http://onchain.test")
                .defaultHeader("X-Internal-Api-Key", "test-secret")
                .build();
    }
}
