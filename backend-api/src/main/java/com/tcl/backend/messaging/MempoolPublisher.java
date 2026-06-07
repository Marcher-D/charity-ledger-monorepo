package com.tcl.backend.messaging;

import com.tcl.backend.transactions.TransactionCache;

public interface MempoolPublisher {

    void publish(TransactionCache transaction, String senderAddress, String targetAddress);
}
