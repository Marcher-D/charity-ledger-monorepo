package com.tcl.backend.onchain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

// One of the five genesis validator nodes spawned by the backend at startup.
// The chain's GenesisConfig funds (10,000) and stakes (5,000) exactly the
// first five node identities; they belong to no user. The row count is also
// the bootstrap idempotency marker for GenesisNodeInitializer.
@Entity
@Table(name = "genesis_nodes")
public class GenesisNode {

    @Id
    @Column(name = "node_index")
    private Integer nodeIndex;

    // 40-char hex on-chain address of the genesis node.
    @Column(nullable = false, length = 64)
    private String address;

    // Base64 Ed25519 public key of the genesis node.
    @Column(name = "public_key", nullable = false, length = 64)
    private String publicKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Integer getNodeIndex() {
        return nodeIndex;
    }

    public void setNodeIndex(Integer nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
