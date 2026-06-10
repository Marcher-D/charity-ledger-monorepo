package com.tcl.backend.messaging;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Small factory shared by the orchestrator and node-signer clients.
// It centralizes base URL and timeout setup.
final class OnChainRestClientFactory {

    private OnChainRestClientFactory() {
    }

    static RestClient create(String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
