-- V2: On-chain node identity + reward bookkeeping.
--
-- Every user and every approved campaign owns one blockchain node, spawned by
-- the NodeOrchestrator. We persist the node's index (selects its signing
-- service host node-<index>) and its Base64 Ed25519 public key (used as
-- sender/receiver in on-chain transactions). The 40-hex on-chain address
-- already lives in users.wallet_address / campaigns.on_chain_address.

ALTER TABLE users
    ADD COLUMN public_key VARCHAR(64),
    ADD COLUMN node_index INT;

ALTER TABLE campaigns
    ADD COLUMN public_key VARCHAR(64),
    ADD COLUMN node_index INT;

-- The five genesis validator nodes spawned by the backend at startup.
-- They are the only funded/staked identities at round 0 and map to no user.
-- The row count doubles as the bootstrap idempotency marker.
CREATE TABLE genesis_nodes (
    node_index INT PRIMARY KEY,
    address    VARCHAR(64) NOT NULL,
    public_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Proposer-reward dedup. Every blockchain node publishes the same
-- BLOCK_FINALIZED event, so the backend receives ~N copies; this table makes
-- the reward credit exactly-once across backend restarts. Only identifiers are
-- stored — no block data.
CREATE TABLE processed_rewards (
    block_hash   VARCHAR(128) PRIMARY KEY,
    round        BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
