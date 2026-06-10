package com.tcl.backend.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

// Dedup marker for proposer-reward events. Every blockchain node publishes the
// same BLOCK_FINALIZED event, so the backend receives ~N copies; inserting this
// row first makes the reward credit exactly-once across backend restarts.
// Only identifiers are stored — no block data travels to or lives in the backend.
@Entity
@Table(name = "processed_rewards")
public class ProcessedReward {

    @Id
    @Column(name = "block_hash", length = 128)
    private String blockHash;

    // Consensus round the reward belongs to (informational/debugging only).
    @Column(nullable = false)
    private long round;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public long getRound() {
        return round;
    }

    public void setRound(long round) {
        this.round = round;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
