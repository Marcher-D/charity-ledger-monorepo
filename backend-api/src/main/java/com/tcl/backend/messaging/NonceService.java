package com.tcl.backend.messaging;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

// Generates the global nonce_order required by the project lead update log.
// nonce_order is added to outbound transaction JSON before publishing to mempool.
// It is intentionally not stored in PostgreSQL.
@Service
public class NonceService {

    // AtomicLong is safe when multiple HTTP requests ask for a nonce at the same time.
    // Start from current time so values normally keep increasing even after app restart.
    private final AtomicLong lastNonceOrder = new AtomicLong(System.currentTimeMillis());

    // Return the next globally increasing nonce_order value.
    public long nextNonceOrder() {
        while (true) {
            // Read the current last value.
            long previous = lastNonceOrder.get();
            // Candidate is either current time or previous + 1, whichever is bigger.
            // This prevents duplicate/non-increasing values during high concurrency.
            long candidate = Math.max(System.currentTimeMillis(), previous + 1);
            // compareAndSet means:
            // "If lastNonceOrder is still previous, replace it with candidate."
            // If another request changed it first, this returns false and we retry.
            if (lastNonceOrder.compareAndSet(previous, candidate)) {
                return candidate;
            }
        }
    }
}
