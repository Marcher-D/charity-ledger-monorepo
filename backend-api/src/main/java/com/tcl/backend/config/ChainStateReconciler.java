package com.tcl.backend.config;

import com.tcl.backend.messaging.MempoolPublisher;
import com.tcl.backend.messaging.NodeSignRequest;
import com.tcl.backend.messaging.NodeSignResponse;
import com.tcl.backend.messaging.NodeSignerClient;
import com.tcl.backend.messaging.OnChainTransactionMessage;
import com.tcl.backend.messaging.OrchestratorHealth;
import com.tcl.backend.messaging.OrchestratorWalletService;
import com.tcl.backend.messaging.SystemKeySigner;
import com.tcl.backend.onchain.ChainSyncState;
import com.tcl.backend.onchain.ChainSyncStateRepository;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/*
 * Replays user balances onto a freshly reset blockchain.
 *
 * The consensus core only reliably supports nodes that boot together at
 * round 0, so the orchestrator wipes chain state on every stack start
 * (CHAIN_RESET_ON_START) and bumps a "chain epoch". The web database persists
 * across restarts — so after a reset, users would have off-chain balances but
 * empty on-chain accounts, and every donation/stake would be rejected with
 * INSUFFICIENT_FUNDS.
 *
 * This runner compares the orchestrator's chain epoch with the last epoch
 * recorded in onchain_sync. When they differ it re-funds every user's on-chain
 * account to mirror the off-chain ledger:
 *   1. system-signed DEPOSIT of (available + pending + staked)
 *   2. user-node-signed STAKE of the staked portion (after the deposits settle)
 *
 * These transactions are published WITHOUT TransactionCache rows, so their
 * TXN_VALIDATED events match nothing off-chain and change no web balances —
 * they only restore the chain side of the mirror.
 */
@Component
@Order(2) // after GenesisNodeInitializer: validators must exist before txs can settle
public class ChainStateReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChainStateReconciler.class);

    // One consensus round is seconds; this comfortably covers the gap between
    // publishing the deposits and the stakes needing those funds on-chain.
    private static final long STAKE_DELAY_MS = 45_000;
    private static final long FIRST_VALID = 0L;
    private static final long LAST_VALID = 999_999_999L;

    private final ChainSyncStateRepository chainSyncStateRepository;
    private final UserRepository userRepository;
    private final OrchestratorWalletService orchestratorWalletService;
    private final SystemKeySigner systemKeySigner;
    private final NodeSignerClient nodeSignerClient;
    private final MempoolPublisher mempoolPublisher;

    public ChainStateReconciler(
            ChainSyncStateRepository chainSyncStateRepository,
            UserRepository userRepository,
            OrchestratorWalletService orchestratorWalletService,
            SystemKeySigner systemKeySigner,
            NodeSignerClient nodeSignerClient,
            MempoolPublisher mempoolPublisher
    ) {
        this.chainSyncStateRepository = chainSyncStateRepository;
        this.userRepository = userRepository;
        this.orchestratorWalletService = orchestratorWalletService;
        this.systemKeySigner = systemKeySigner;
        this.nodeSignerClient = nodeSignerClient;
        this.mempoolPublisher = mempoolPublisher;
    }

    @Override
    public void run(ApplicationArguments args) {
        // GenesisNodeInitializer already waited for the orchestrator, so a
        // single health call is enough here.
        OrchestratorHealth health = orchestratorWalletService.health();
        long currentEpoch = health.chainEpoch();
        ChainSyncState state = chainSyncStateRepository.findById(1).orElse(null);
        long syncedEpoch = state == null ? -1 : state.getChainEpoch();

        if (syncedEpoch == currentEpoch) {
            log.info("Chain epoch {} already reconciled — skipping balance replay", currentEpoch);
            return;
        }

        List<AppUser> users = userRepository.findAll().stream()
                .filter(user -> user.getPublicKey() != null && user.getNodeIndex() != null)
                .toList();

        log.info("Chain epoch changed ({} -> {}): replaying balances of {} user(s) onto the fresh chain",
                syncedEpoch, currentEpoch, users.size());

        // Mark the epoch first so a crash mid-replay cannot double-fund on the
        // next boot (a partial replay is recoverable by a full reset; a double
        // replay would silently inflate on-chain balances).
        ChainSyncState newState = state == null ? new ChainSyncState() : state;
        newState.setChainEpoch(currentEpoch);
        chainSyncStateRepository.save(newState);

        if (users.isEmpty()) {
            return;
        }

        // Replay asynchronously: stakes must wait for deposits to settle, and
        // backend startup should not block on consensus rounds.
        Thread replay = new Thread(() -> replayBalances(users), "chain-state-reconciler");
        replay.setDaemon(true);
        replay.start();
    }

    private void replayBalances(List<AppUser> users) {
        int deposits = 0;
        for (AppUser user : users) {
            BigDecimal total = sum(user.getAvailableBalance(), user.getPendingBalance(), user.getStakedBalance());
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            try {
                OnChainTransactionMessage deposit = systemKeySigner.signSystemTransaction(
                        "DEPOSIT", user.getPublicKey(), total,
                        FIRST_VALID, LAST_VALID, Instant.now().toEpochMilli(),
                        "CHAIN_EPOCH_RECONCILIATION");
                mempoolPublisher.publishPrebuilt(deposit);
                deposits++;
            } catch (RuntimeException e) {
                log.error("Failed to replay deposit for user {}: {}", user.getAccountId(), e.getMessage());
            }
        }
        log.info("Reconciliation: {} deposit(s) published; waiting {}s before replaying stakes",
                deposits, STAKE_DELAY_MS / 1000);

        try {
            Thread.sleep(STAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        int stakes = 0;
        for (AppUser user : users) {
            BigDecimal staked = safe(user.getStakedBalance());
            if (staked.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            try {
                NodeSignResponse signed = nodeSignerClient.sign(user.getNodeIndex(), new NodeSignRequest(
                        "STAKE", null, staked.doubleValue(),
                        FIRST_VALID, LAST_VALID, Instant.now().toEpochMilli(),
                        "CHAIN_EPOCH_RECONCILIATION"));
                mempoolPublisher.publishPrebuilt(new OnChainTransactionMessage(
                        signed.txId(), signed.type(), signed.senderPubKey(), signed.receiverPubKey(),
                        signed.amount(), signed.firstValid(), signed.lastValid(), signed.timestamp(),
                        signed.note(), signed.ed25519Signature()));
                stakes++;
            } catch (RuntimeException e) {
                log.error("Failed to replay stake for user {}: {}", user.getAccountId(), e.getMessage());
            }
        }
        log.info("Reconciliation complete: {} stake(s) replayed", stakes);
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(safe(value));
        }
        return total;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
