package com.tcl.backend.messaging;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Small factory shared by the wallet and signing clients.
// It centralizes base URL, timeout, and internal API-key header setup.
final class OnChainRestClientFactory {

    private OnChainRestClientFactory() {
    }

    static RestClient create(OnChainProperties properties) {
        return create(properties, RestClient.builder());
    }

    static RestClient create(OnChainProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeoutMs());
        requestFactory.setReadTimeout(properties.timeoutMs());

        return builder
                .baseUrl(properties.walletBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Api-Key", properties.internalApiKey())
                .build();
    }
}
