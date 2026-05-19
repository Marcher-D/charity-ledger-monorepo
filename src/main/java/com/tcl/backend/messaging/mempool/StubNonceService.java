package com.tcl.backend.messaging.mempool;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StubNonceService implements NonceService {

    // Starts at 1 and increments safely across multiple concurrent requests
    private final AtomicLong temporaryCounter = new AtomicLong(1);

    @Override
    public long nextNonce() {
        return temporaryCounter.getAndIncrement();
    }
}