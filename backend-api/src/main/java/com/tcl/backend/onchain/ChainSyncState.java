package com.tcl.backend.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

// Singleton row (id = 1) remembering the last chain epoch this backend
// reconciled. See ChainStateReconciler.
@Entity
@Table(name = "onchain_sync")
public class ChainSyncState {

    @Id
    private Integer id = 1;

    @Column(name = "chain_epoch", nullable = false)
    private long chainEpoch;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        syncedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public long getChainEpoch() {
        return chainEpoch;
    }

    public void setChainEpoch(long chainEpoch) {
        this.chainEpoch = chainEpoch;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
