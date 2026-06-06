package com.tcl.backend.messaging;

import com.tcl.backend.campaigns.Campaign;
import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.campaigns.CampaignStatus;
import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.transactions.TransactionCacheRepository;
import com.tcl.backend.transactions.TransactionStatus;
import com.tcl.backend.transactions.TransactionType;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * Business logic for messages received from event_bus_queue.
 *
 * OnChainEventListener only receives the RabbitMQ message. This service decides
 * what that message means and updates PostgreSQL.
 *
 * Current expected event payload shapes:
 *
 * BLOCK_SUCCESS:
 * {
 *   "successfulTxIds": ["transaction-uuid-1", "transaction-uuid-2"]
 * }
 *
 * TX_FAIL / TX_EXPIRED:
 * {
 *   "txId": "transaction-uuid"
 * }
 *
 * BLOCK_FAIL with slashing:
 * {
 *   "penaltyAction": "SLASH_STAKE",
 *   "proposerAddress": "wallet-address"
 * }
 *
 * This class is still off-chain cache logic. The blockchain/consensus side is
 * responsible for deciding whether the transaction/block really succeeded.
 */
@Service
public class OnChainEventService {

    // If a proposer is slashed, remove 1% of their staked balance in our cache.
    private static final BigDecimal PROPOSER_SLASH_RATE = new BigDecimal("0.01");

    // Reads and updates cached transaction rows.
    private final TransactionCacheRepository transactionRepository;
    // Updates campaign state/current raised after campaign-related transactions.
    private final CampaignRepository campaignRepository;
    // Updates user balances and validator state.
    private final UserRepository userRepository;
    /*
     * Prevents applying the same Event Bus message twice while this backend process
     * is running.
     *
     * ConcurrentHashMap.newKeySet() creates a thread-safe Set. This matters because
     * RabbitMQ listener threads could process messages concurrently.
     *
     * Note: this is in-memory only. If the backend restarts, this Set becomes empty.
     */
    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    // Constructor injection: Spring provides repository beans.
    public OnChainEventService(
            TransactionCacheRepository transactionRepository,
            CampaignRepository campaignRepository,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
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
        /*
         * Idempotency check:
         *
         * If eventId is not null, add it to processedEventIds.
         * Set.add(...) returns false when the value already exists.
         * So if we already processed this eventId, return immediately.
         */
        if (event.eventId() != null && !processedEventIds.add(event.eventId())) {
            return;
        }

        // Route the message to the correct handler based on event type.
        if (event.eventType() == EventBusEventType.BLOCK_SUCCESS) {
            applyBlockSuccess(event.payload());
        } else if (event.eventType() == EventBusEventType.TX_FAIL || event.eventType() == EventBusEventType.TX_EXPIRED) {
            applyTransactionFailure(event.payload());
        } else if (event.eventType() == EventBusEventType.BLOCK_FAIL) {
            applyBlockFailure(event.payload());
        }
    }

    /*
     * Handles BLOCK_SUCCESS.
     *
     * Preferred payload format:
     * payload.successfulTxIds = list of backend transaction UUIDs.
     *
     * Fallback supported by this code:
     * payload.txId = one backend transaction UUID.
     */
    private void applyBlockSuccess(Map<String, Object> payload) {
        Object successfulTxIds = payload == null ? null : payload.get("successfulTxIds");
        if (successfulTxIds instanceof List<?> ids) {
            /*
             * ids is a List<?> because payload values are generic Object values.
             * We convert each item to UUID, then settle that transaction as success.
             */
            ids.stream()
                    .map(this::uuidValue)
                    .forEach(this::settleSuccess);
            return;
        }

        // Single-transaction fallback.
        settleSuccess(uuidValue(payload == null ? null : payload.get("txId")));
    }

    /*
     * Marks one pending transaction as successful and applies its final state.
     *
     * txId here is the backend transactions_cache.id UUID, not txHash.
     */
    private void settleSuccess(UUID txId) {
        if (txId == null) {
            return;
        }

        // If transaction is missing, ignore the event instead of crashing listener.
        TransactionCache transaction = transactionRepository.findById(txId).orElse(null);
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
        } else if (transaction.getType() == TransactionType.SLASH) {
            // Slash removes stake, but never lets staked balance go below zero.
            user.setStakedBalance(balance(user.getStakedBalance()).subtract(amount).max(BigDecimal.ZERO));
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.CREATE) {
            // Campaign creation is not open to donations until on-chain create succeeds.
            openCreatedCampaign(transaction);
        } else if (transaction.getType() == TransactionType.VALIDATOR_TOGGLE) {
            // Note stores "OPT_IN" or "OPT_OUT" from TransactionService.
            user.setActiveValidator("OPT_IN".equalsIgnoreCase(transaction.getNote()));
            userRepository.save(user);
        }
    }

    /*
     * Handles TX_FAIL and TX_EXPIRED.
     *
     * Expected payload:
     * { "txId": "transaction-uuid" }
     */
    private void applyTransactionFailure(Map<String, Object> payload) {
        UUID txId = uuidValue(payload == null ? null : payload.get("txId"));
        if (txId == null) {
            return;
        }

        TransactionCache transaction = transactionRepository.findById(txId).orElse(null);
        if (transaction == null || transaction.getStatus() != TransactionStatus.PENDING) {
            return;
        }

        // Mark transaction failed, then undo any money that was locked before publish.
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        refundLockedMoney(transaction);
    }

    /*
     * Handles BLOCK_FAIL.
     *
     * In this implementation, BLOCK_FAIL is only used for proposer slashing when
     * payload.penaltyAction is "SLASH_STAKE".
     */
    private void applyBlockFailure(Map<String, Object> payload) {
        if (payload == null || !"SLASH_STAKE".equals(payload.get("penaltyAction"))) {
            return;
        }

        // Find the user by wallet address because block failure identifies proposer address.
        String proposerAddress = stringValue(payload.get("proposerAddress"));
        if (proposerAddress == null || proposerAddress.isBlank()) {
            return;
        }

        userRepository.findByWalletAddress(proposerAddress).ifPresent(user -> {
            BigDecimal staked = balance(user.getStakedBalance());
            // 1% slash, rounded down to 2 decimals.
            BigDecimal slashAmount = staked.multiply(PROPOSER_SLASH_RATE).setScale(2, RoundingMode.DOWN);
            if (slashAmount.compareTo(BigDecimal.ZERO) > 0) {
                user.setStakedBalance(staked.subtract(slashAmount));
                userRepository.save(user);
            }
        });
    }

    /*
     * Undo off-chain locked money when a transaction fails/expires.
     *
     * This only affects money we previously moved into pending_balance before
     * publishing to RabbitMQ.
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

    // Apply a successful CREATE transaction to the campaign cache.
    private void openCreatedCampaign(TransactionCache transaction) {
        Campaign campaign = transaction.getCampaign();
        if (campaign == null) {
            return;
        }

        campaign.setStatus(CampaignStatus.OPEN);
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

    /*
     * Convert a generic payload value into UUID.
     *
     * Rabbit/Jackson may give us a real UUID object or a String depending on how
     * the JSON was deserialized. This helper supports both.
     */
    private UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        return null;
    }

    // Convert generic payload value to String safely.
    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
