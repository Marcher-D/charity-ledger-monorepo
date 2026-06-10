package com.tcl.backend.messaging;

/*
 * Event types published by the blockchain's EventGateway to event_bus_queue.
 * The wire names are fixed by the on-chain core code.
 *
 * BLOCK_FINALIZED is a proposer-reward notification only: no block data travels
 * to the backend. Its payload carries the proposer identity and the rewardAmount
 * (2% of the DONATE transactions in that round), plus round/blockHash identifiers
 * used purely for de-duplication.
 */
public enum EventBusEventType {
    TXN_VALIDATED,
    TXN_REJECTED,
    BLOCK_FINALIZED
}
