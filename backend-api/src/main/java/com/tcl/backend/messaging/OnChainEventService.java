package com.tcl.backend.messaging;

import com.tcl.backend.campaigns.Campaign;
import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.campaigns.CampaignStatus;
import com.tcl.backend.onchain.ProcessedReward;
import com.tcl.backend.onchain.ProcessedRewardRepository;
import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.transactions.TransactionCacheRepository;
import com.tcl.backend.transactions.TransactionStatus;
import com.tcl.backend.transactions.TransactionType;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Business logic for messages received from event_bus_queue.
 *
 * OnChainEventListener only receives/parses the RabbitMQ message. This service
 * decides what that message means and updates PostgreSQL.
 *
 * Events published by the blockchain's EventGateway (wire format is fixed by
 * the on-chain core — see offchain_integration_specification.md):
 *
 * TXN_VALIDATED  — transaction committed in a finalized block:
 *   payload: { "txId": "sha256-hex", "type": "DONATE", "amount": 25.0 }
 *
 * TXN_REJECTED   — transaction failed validation or expired in the mempool:
 *   payload: { "txId": "sha256-hex", "type": "STAKE", "reason": "INSUFFICIENT_FUNDS" }
 *
 * BLOCK_FINALIZED — proposer-reward notification (NO block data):
 *   payload: { "round": 4, "blockHash": "...", "transactionsIncluded": 10,
 *              "rewardAmount": 4.5, "proposer": { "publicKey": "...", "address": "..." } }
 *
 * txId here is the on-chain transaction id (SHA-256 of the canonical signable
 * data). We stored it in transactions_cache.tx_hash when publishing, so events
 * are correlated via findByTxHash.
 *
 * Duplicate handling: EVERY blockchain node publishes the same events, so the
 * backend receives ~N copies of each. eventIds are deterministic across nodes,
 * so the in-memory set drops most duplicates cheaply; the status==PENDING guard
 * (transactions) and the processed_rewards table (rewards) make the database
 * updates idempotent even across backend restarts.
 */
@Service
public class OnChainEventService {

    private static final Logger log = LoggerFactory.getLogger(OnChainEventService.class);

    // Reads and updates cached transaction rows.
    private final TransactionCacheRepository transactionRepository;
    // Updates campaign state/current raised after campaign-related transactions.
    private final CampaignRepository campaignRepository;
    // Updates user balances and validator state.
    private final UserRepository userRepository;
    // Exactly-once guard for proposer reward credits.
    private final ProcessedRewardRepository processedRewardRepository;

    /*
     * Prevents applying the same Event Bus message twice while this backend process
     * is running. In-memory only; restart safety comes from the DB-level guards.
     */
    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    // Constructor injection: Spring provides repository beans.
    public OnChainEventService(
            TransactionCacheRepository transactionRepository,
            CampaignRepository campaignRepository,
            UserRepository userRepository,
            ProcessedRewardRepository processedRewardRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.processedRewardRepository = processedRewardRepository;
    }

    /*
     * Main entry method called by OnChainEventListener.
     *
     * @Transactional means all database updates inside one event are committed
     * together. If an error happens midway, Spring rolls back the database changes
     * from this method call.
     */
    @Transactional
    public void apply(EventBusMessage event) {
        // Set.add(...) returns false when the value already exists -> duplicate.
        if (event.eventId() != null && !processedEventIds.add(event.eventId())) {
            return;
        }

        // Route the message to the correct handler based on event type.
        if (event.eventType() == EventBusEventType.TXN_VALIDATED) {
            settleSuccess(stringValue(payloadValue(event, "txId")));
        } else if (event.eventType() == EventBusEventType.TXN_REJECTED) {
            settleFailure(stringValue(payloadValue(event, "txId")), stringValue(payloadValue(event, "reason")));
        } else if (event.eventType() == EventBusEventType.BLOCK_FINALIZED) {
            applyProposerReward(event.payload());
        }
    }

    /*
     * Marks one pending transaction as successful and applies its final state.
     * txId is the on-chain transaction id stored in transactions_cache.tx_hash.
     */
    private void settleSuccess(String txId) {
        if (txId == null || txId.isBlank()) {
            return;
        }

        // If transaction is missing, ignore the event instead of crashing listener.
        TransactionCache transaction = transactionRepository.findByTxHash(txId).orElse(null);
        // Only pending transactions should be finalized. SUCCESS/FAILED rows are left alone.
        if (transaction == null || transaction.getStatus() != TransactionStatus.PENDING) {
            return;
        }

        // First mark the cached transaction itself as SUCCESS.
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

        AppUser user = transaction.getUser();
        BigDecimal amount = amount(transaction);

        /*
         * Different transaction types affect the off-chain cache differently.
         *
         * pending_balance means "money temporarily locked while waiting for on-chain
         * confirmation". On success, we release that pending amount and apply the final
         * result.
         */
        if (transaction.getType() == TransactionType.DONATE) {
            // Donation money was already moved available -> pending before publish.
            releasePending(user, amount);
            userRepository.save(user);
            // Then increase campaign.current_raised.
            applyDonationToCampaign(transaction, amount);
        } else if (transaction.getType() == TransactionType.STAKE) {
            // Staking success moves money from pending into staked_balance.
            releasePending(user, amount);
            user.setStakedBalance(balance(user.getStakedBalance()).add(amount));
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.UNSTAKE) {
            // Unstake success moves money from pending back to available_balance.
            releasePending(user, amount);
            user.setAvailableBalance(balance(user.getAvailableBalance()).add(amount));
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.WITHDRAW) {
            // Withdraw success means pending money left the app/on-chain account.
            releasePending(user, amount);
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.DEPOSIT
                || transaction.getType() == TransactionType.REWARD
                || transaction.getType() == TransactionType.REFUND) {
            // These increase available_balance after on-chain success.
            user.setAvailableBalance(balance(user.getAvailableBalance()).add(amount));
            userRepository.save(user);
        }
        log.info("On-chain TXN_VALIDATED settled: type={} txId={}", transaction.getType(), txId);
    }

    /*
     * Handles TXN_REJECTED (invalid signature, insufficient funds, or mempool
     * timeout). Marks the transaction failed and refunds any locked money.
     */
    private void settleFailure(String txId, String reason) {
        if (txId == null || txId.isBlank()) {
            return;
        }

        TransactionCache transaction = transactionRepository.findByTxHash(txId).orElse(null);
        if (transaction == null || transaction.getStatus() != TransactionStatus.PENDING) {
            return;
        }

        // Mark transaction failed, then undo any money that was locked before publish.
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        refundLockedMoney(transaction);
        log.info("On-chain TXN_REJECTED settled: type={} txId={} reason={}", transaction.getType(), txId, reason);
    }

    /*
     * Handles BLOCK_FINALIZED — the proposer-reward notification.
     *
     * The chain makes NO state change for rewards; the backend is fully
     * responsible for crediting rewardAmount (2% of the round's DONATE
     * transactions) to the proposer's web account. Only the proposer address and
     * rewardAmount are consumed; round/blockHash are dedup identifiers.
     */
    private void applyProposerReward(Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        String blockHash = stringValue(payload.get("blockHash"));
        if (blockHash == null || blockHash.isBlank()) {
            return;
        }
        // Exactly-once guard: every node sends this event; first writer wins.
        if (processedRewardRepository.existsById(blockHash)) {
            return;
        }
        ProcessedReward marker = new ProcessedReward();
        marker.setBlockHash(blockHash);
        marker.setRound(longValue(payload.get("round")));
        processedRewardRepository.save(marker);

        BigDecimal rewardAmount = decimalValue(payload.get("rewardAmount"));
        if (rewardAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // proposer: { "publicKey": "...", "address": "..." }
        Object proposerNode = payload.get("proposer");
        String proposerAddress = proposerNode instanceof Map<?, ?> proposer
                ? stringValue(proposer.get("address"))
                : null;
        if (proposerAddress == null || proposerAddress.isBlank()) {
            return;
        }

        AppUser proposerUser = userRepository.findByWalletAddress(proposerAddress).orElse(null);
        if (proposerUser == null) {
            // Genesis validator nodes map to no web user — expected, just log.
            log.info("Proposer reward {} for unmapped address {} (genesis node) — skipped",
                    rewardAmount, proposerAddress);
            return;
        }

        // Credit the reward and keep a SUCCESS history row (txHash = blockHash).
        proposerUser.setAvailableBalance(balance(proposerUser.getAvailableBalance()).add(rewardAmount));
        userRepository.save(proposerUser);

        TransactionCache reward = new TransactionCache();
        reward.setType(TransactionType.REWARD);
        reward.setUser(proposerUser);
        reward.setAmount(rewardAmount);
        reward.setNote("PROPOSER_REWARD round=" + longValue(payload.get("round")));
        reward.setStatus(TransactionStatus.SUCCESS);
        reward.setTxHash(blockHash);
        transactionRepository.save(reward);
        log.info("Proposer reward {} credited to user {} for round {}",
                rewardAmount, proposerUser.getAccountId(), longValue(payload.get("round")));
    }

    /*
     * Undo off-chain locked money when a transaction fails/expires.
     *
     * This only affects money we previously moved into pending_balance before
     * publishing to RabbitMQ. DEPOSIT locks nothing, so it needs no refund.
     */
    private void refundLockedMoney(TransactionCache transaction) {
        AppUser user = transaction.getUser();
        BigDecimal amount = amount(transaction);

        if (transaction.getType() == TransactionType.DONATE
                || transaction.getType() == TransactionType.STAKE
                || transaction.getType() == TransactionType.WITHDRAW) {
            // These originally locked available_balance -> pending_balance.
            releasePending(user, amount);
            user.setAvailableBalance(balance(user.getAvailableBalance()).add(amount));
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.UNSTAKE) {
            // Unstake originally locked staked_balance -> pending_balance.
            releasePending(user, amount);
            user.setStakedBalance(balance(user.getStakedBalance()).add(amount));
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.CREATE && transaction.getCampaign() != null) {
            // Failed campaign creation returns campaign to PENDING admin-approved/request state.
            Campaign campaign = transaction.getCampaign();
            campaign.setStatus(CampaignStatus.PENDING);
            campaignRepository.save(campaign);
        }
    }

    // Apply a successful DONATE transaction to the campaign cache.
    private void applyDonationToCampaign(TransactionCache transaction, BigDecimal amount) {
        Campaign campaign = transaction.getCampaign();
        if (campaign == null) {
            return;
        }

        campaign.setCurrentRaised(balance(campaign.getCurrentRaised()).add(amount));
        // If target is reached, mark campaign complete.
        if (campaign.getCurrentRaised().compareTo(campaign.getTargetAmount()) >= 0) {
            campaign.setStatus(CampaignStatus.COMPLETED);
        }
        campaignRepository.save(campaign);
    }

    // Subtract amount from pending_balance after success or failure handling.
    private void releasePending(AppUser user, BigDecimal amount) {
        user.setPendingBalance(balance(user.getPendingBalance()).subtract(amount));
    }

    // Normalize null transaction amount to zero before math.
    private BigDecimal amount(TransactionCache transaction) {
        return balance(transaction.getAmount());
    }

    // Prevent NullPointerException in balance math.
    private BigDecimal balance(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Object payloadValue(EventBusMessage event, String key) {
        return event.payload() == null ? null : event.payload().get(key);
    }

    // Convert generic payload value to String safely.
    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    // Convert generic payload value (Integer/Long/Double/String) to long safely.
    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    // Convert generic payload value to a 2-decimal BigDecimal money amount.
    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.DOWN);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.DOWN);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim()).setScale(2, RoundingMode.DOWN);
            } catch (NumberFormatException ignored) {
            }
        }
        return BigDecimal.ZERO;
    }
}
