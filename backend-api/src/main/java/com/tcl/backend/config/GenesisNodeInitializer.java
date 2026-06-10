package com.tcl.backend.config;

import com.tcl.backend.messaging.NodeIdentity;
import com.tcl.backend.messaging.OnChainProperties;
import com.tcl.backend.messaging.WalletService;
import com.tcl.backend.onchain.GenesisNode;
import com.tcl.backend.onchain.GenesisNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/*
 * Spawns the genesis validator nodes when the system boots for the first time.
 *
 * The chain's GenesisConfig funds (10,000) and stakes (5,000) exactly the first
 * five node identities; without them no proposer can ever win sortition and no
 * block is ever produced. So after the infrastructure containers are up
 * (rabbitmq, mempool, orchestrator) the backend asks the orchestrator to spawn
 * nodes 0..4 once, records them in genesis_nodes, and never does it again.
 *
 * Every node spawned afterwards (user registration, campaign approval) starts
 * with 0 balance / 0 stake.
 *
 * docker-compose start order is not a guarantee that the orchestrator is ready,
 * so the first spawn call is retried with a backoff instead of failing startup.
 */
@Component
public class GenesisNodeInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GenesisNodeInitializer.class);

    private static final int MAX_ATTEMPTS = 30;
    private static final long RETRY_DELAY_MS = 2_000;

    private final GenesisNodeRepository genesisNodeRepository;
    private final WalletService walletService;
    private final OnChainProperties properties;

    public GenesisNodeInitializer(
            GenesisNodeRepository genesisNodeRepository,
            WalletService walletService,
            OnChainProperties properties
    ) {
        this.genesisNodeRepository = genesisNodeRepository;
        this.walletService = walletService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long existing = genesisNodeRepository.count();
        int target = properties.genesisNodeCount();
        if (existing >= target) {
            log.info("Genesis nodes already provisioned ({} recorded) — skipping bootstrap", existing);
            return;
        }

        log.info("Bootstrapping {} genesis validator nodes via orchestrator at {}",
                target - existing, properties.orchestratorBaseUrl());

        for (long i = existing; i < target; i++) {
            NodeIdentity identity = spawnWithRetry();

            GenesisNode node = new GenesisNode();
            node.setNodeIndex(identity.nodeIndex());
            node.setAddress(identity.address());
            node.setPublicKey(identity.publicKey());
            genesisNodeRepository.save(node);

            log.info("Genesis node {} spawned: address={} publicKey={}...",
                    identity.nodeIndex(), identity.address(),
                    identity.publicKey().substring(0, Math.min(12, identity.publicKey().length())));

            if (identity.nodeIndex() >= target) {
                // The orchestrator's persisted index is ahead of our genesis count:
                // it spawned nodes before this database existed. The genesis stake
                // belongs to indexes 0..target-1 only, so flag it loudly.
                log.warn("Orchestrator assigned node index {} during genesis bootstrap (expected < {}). "
                        + "Orchestrator and database state are out of sync — start both from clean volumes.",
                        identity.nodeIndex(), target);
            }
        }
    }

    // The genesis nodes are mandatory for the chain to run at all, so be patient
    // with the orchestrator rather than failing backend startup.
    private NodeIdentity spawnWithRetry() throws InterruptedException {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return walletService.provisionUserNode(null);
            } catch (RuntimeException e) {
                lastFailure = e;
                log.info("Orchestrator not ready (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
        throw new IllegalStateException("Could not reach the node orchestrator to spawn genesis nodes", lastFailure);
    }
}
