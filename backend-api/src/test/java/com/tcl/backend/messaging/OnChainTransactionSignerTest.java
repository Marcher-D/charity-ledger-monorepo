package com.tcl.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tcl.backend.common.ApiException;
import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.transactions.TransactionType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OnChainTransactionSignerTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OnChainTransactionSigner signer = new OnChainTransactionSigner(restClient(), new MoneyFormatter());

    @Test
    void signRequestUsesExactOutboundTransactionFields() {
        TransactionCache transaction = new TransactionCache();
        transaction.setId(UUID.randomUUID());
        transaction.setType(TransactionType.STAKE);
        transaction.setAmount(new BigDecimal("10"));
        Map<String, Object> payload = Map.of("note", "STAKE");

        server.expect(requestTo("http://onchain.test/internal/v1/transactions/sign"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "test-secret"))
                .andExpect(content().json("""
                        {
                          "txId": "%s",
                          "nonce_order": 123,
                          "type": "STAKE",
                          "senderAddress": "0xsender",
                          "targetAddress": "STAKE_POOL",
                          "amount": "10.00",
                          "timestamp": 1778832956997,
                          "payload": {
                            "note": "STAKE"
                          }
                        }
                        """.formatted(transaction.getId())))
                .andRespond(withSuccess("""
                        {
                          "publicKey": "04abc",
                          "signature": "3045",
                          "txHash": "hash-123",
                          "algorithm": "ECDSA_SECP256K1",
                          "signedAt": 1778832957001
                        }
                        """, MediaType.APPLICATION_JSON));

        SignedTransaction signed = signer.sign(
                transaction,
                123,
                "0xsender",
                "STAKE_POOL",
                1778832956997L,
                payload
        );

        assertThat(signed.publicKey()).isEqualTo("04abc");
        assertThat(signed.signature()).isEqualTo("3045");
        assertThat(signed.txHash()).isEqualTo("hash-123");
        server.verify();
    }

    @Test
    void invalidSignatureResponseBecomesBadGateway() {
        TransactionCache transaction = new TransactionCache();
        transaction.setId(UUID.randomUUID());
        transaction.setType(TransactionType.STAKE);
        transaction.setAmount(BigDecimal.TEN);

        server.expect(requestTo("http://onchain.test/internal/v1/transactions/sign"))
                .andRespond(withSuccess("""
                        {
                          "publicKey": "",
                          "signature": "3045",
                          "txHash": "hash-123"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> signer.sign(transaction, 1, "0xsender", "STAKE_POOL", 10L, Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessage("On-chain signing service returned invalid signature data");
    }

    private RestClient restClient() {
        return builder
                .baseUrl("http://onchain.test")
                .defaultHeader("X-Internal-Api-Key", "test-secret")
                .build();
    }
}
