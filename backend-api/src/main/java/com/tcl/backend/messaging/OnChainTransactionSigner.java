package com.tcl.backend.messaging;

import com.tcl.backend.common.ApiException;
import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.transactions.TransactionCache;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Real TransactionSigner implementation backed by the on-chain signing REST API.
// The backend sends transaction data and receives publicKey/signature/txHash.
@Service
public class OnChainTransactionSigner implements TransactionSigner {

    private final RestClient restClient;
    private final MoneyFormatter moneyFormatter;

    @Autowired
    public OnChainTransactionSigner(OnChainProperties properties, MoneyFormatter moneyFormatter) {
        this.restClient = OnChainRestClientFactory.create(properties);
        this.moneyFormatter = moneyFormatter;
    }

    OnChainTransactionSigner(OnChainProperties properties, RestClient.Builder builder, MoneyFormatter moneyFormatter) {
        this.restClient = OnChainRestClientFactory.create(properties, builder);
        this.moneyFormatter = moneyFormatter;
    }

    OnChainTransactionSigner(RestClient restClient, MoneyFormatter moneyFormatter) {
        this.restClient = restClient;
        this.moneyFormatter = moneyFormatter;
    }

    @Override
    public SignedTransaction sign(
            TransactionCache transaction,
            long nonceOrder,
            String senderAddress,
            String targetAddress,
            long timestamp,
            Map<String, Object> payload
    ) {
        OnChainSignResponse response;
        try {
            response = restClient.post()
                    .uri("/internal/v1/transactions/sign")
                    .body(new OnChainSignRequest(
                            transaction.getId(),
                            nonceOrder,
                            transaction.getType(),
                            senderAddress,
                            targetAddress,
                            moneyFormatter.canonical(transaction.getAmount()),
                            timestamp,
                            payload
                    ))
                    .retrieve()
                    .body(OnChainSignResponse.class);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "On-chain signing service is unavailable");
        }

        if (response == null
                || isBlank(response.publicKey())
                || isBlank(response.signature())
                || isBlank(response.txHash())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "On-chain signing service returned invalid signature data");
        }
        return new SignedTransaction(response.publicKey(), response.signature(), response.txHash());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
