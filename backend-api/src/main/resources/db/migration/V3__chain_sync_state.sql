-- V3: Chain epoch tracking.
--
-- The blockchain's state is reset on every stack start (the consensus core only
-- reliably supports nodes that boot together at round 0). The orchestrator
-- bumps a "chain epoch" on each reset; the backend stores the last epoch it
-- reconciled so that ChainStateReconciler replays user balances onto the fresh
-- chain exactly once per reset.
CREATE TABLE onchain_sync (
    id          INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    chain_epoch BIGINT NOT NULL,
    synced_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
