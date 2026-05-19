package com.tcl.backend.messaging.mempool;


// Later we will put actual NonceService here, here is just a temporary
public interface NonceService {
    long nextNonce();
}