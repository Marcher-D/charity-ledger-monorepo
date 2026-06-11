package network;

import model.Block;
import model.BlockCertificate;
import state.StateEngine;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CatchupService — Background daemon for secure block synchronization.
 * Fixes Bug E (Consensus loop blocking) and Bug R (Unverified catchup blocks).
 */
public class CatchupService {

    private final NetworkEngine networkEngine;
    private final StateEngine stateEngine;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public CatchupService(NetworkEngine networkEngine, StateEngine stateEngine) {
        this.networkEngine = networkEngine;
        this.stateEngine = stateEngine;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CatchupDaemon");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) return;
        running = true;
        // Run every 2 seconds
        scheduler.scheduleWithFixedDelay(this::syncLoop, 2, 2, TimeUnit.SECONDS);
        System.out.println("[CatchupService] Started background sync daemon.");
    }

    public void stop() {
        running = false;
        scheduler.shutdownNow();
    }

    /** Max rounds fetched per sync iteration — keeps SYNC_RESPONSE messages bounded. */
    private static final int BATCH_SIZE = 100;

    private void syncLoop() {
        try {
            long localTip = stateEngine.getLatestRound();
            long networkTip = networkEngine.getNetworkTip();
            long gap = networkTip - localTip;

            if (gap <= 0) return;

            // Fetch in bounded batches; the 2s scheduler iterates until the gap closes.
            long endRound = Math.min(networkTip, localTip + BATCH_SIZE);
            System.out.println("[CatchupService] Gap=" + gap + " detected. Fetching rounds " + (localTip + 1) + " to " + endRound);

            List<Block> fetchedBlocks = networkEngine.activeFetchAsync(localTip + 1, endRound).join();
            if (fetchedBlocks == null || fetchedBlocks.isEmpty()) return;

            for (Block b : fetchedBlocks) {
                // Bug R Fix: Cryptographically verify the block's supermajority certificate before applying!
                if (!isCertifiedBlock(b)) {
                    // Usually transient: the peer served the block inside the small
                    // window before its certificate row was written, or the response
                    // raced a fresh round. The next sync iteration (2s) refetches.
                    System.err.println("[CatchupService] Rejected uncertified or forged block at round " +
                                       b.getRound() + " — will refetch on next sync.");
                    break; // Stop syncing on first invalid block
                }

                // Cert BEFORE block: we serve catch-up peers ourselves, and a block
                // row without its cert row would make them reject the round.
                stateEngine.getDb().saveCertificate(b.getCertificate());
                boolean applied = stateEngine.applyBlock(b);
                if (applied) {
                    System.out.println("[CatchupService] Applied caught-up block: " + b.getRound());
                    networkEngine.setNetworkTip(b.getRound());
                    networkEngine.setNetworkTipHash(b.getHash());
                } else {
                    System.err.println("[CatchupService] Failed to apply block " + b.getRound());
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[CatchupService] Sync error: " + e.getMessage());
        }
    }

    /**
     * Verifies that a fetched block is backed by a genuine >=68% supermajority
     * certificate AND that the certificate actually certifies THIS block.
     *
     * Two cases:
     *  - BOTTOM round: the network agreed on "no block" and every node wrote the
     *    deterministic empty block (hash = sha256(prevHash|BOTTOM|round)). The
     *    certificate's blockHash is BOTTOM_HASH, so it cannot match the block hash —
     *    instead we recompute the canonical empty-block hash from OUR local tip and
     *    require the fetched block to match it (this also enforces chain linkage).
     *  - Real block: the certificate's blockHash must equal the block's hash
     *    (previously unchecked — a peer could pair a valid cert with a different block).
     */
    private boolean isCertifiedBlock(Block b) {
        BlockCertificate cert = b.getCertificate();
        if (cert == null) return false;

        if (cert.isBottom()) {
            if (!b.isEmpty()) return false;
            String expectedHash = crypto.Ed25519Util.sha256Hex(
                stateEngine.getLatestBlockHash() + "|BOTTOM|" + b.getRound());
            if (!expectedHash.equals(b.getHash())) {
                System.err.println("[CatchupService] Bottom block hash mismatch at round " + b.getRound());
                return false;
            }
        } else if (!cert.getBlockHash().equals(b.getHash())) {
            System.err.println("[CatchupService] Certificate/block hash mismatch at round " + b.getRound());
            return false;
        }

        return stateEngine.verifyCertificate(cert, b.getRound());
    }
}
