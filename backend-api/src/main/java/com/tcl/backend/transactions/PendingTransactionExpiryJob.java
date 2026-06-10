package com.tcl.backend.transactions;

import com.tcl.backend.campaigns.Campaign;
import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.campaigns.CampaignStatus;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/*
 * Safety net for transactions whose on-chain outcome never arrives.
 *
 * Normally the blockchain answers every published transaction with
 * TXN_VALIDATED or TXN_REJECTED. But the mock mempool buffers transactions in
 * memory — if it (or the whole stack) restarts while a transaction is in
 * flight, the message is gone and the chain never saw it, so no event will
 * ever come back. Without this job the row stays PENDING forever and any
 * escrowed money stays locked in pending_balance.
 *
 * This job fails such orphans after a generous timeout (far longer than the
 * worst-case consensus confirmation) and refunds locked balances using the
 * same rules as a TXN_REJECTED event.
 */
@Component
public class PendingTransactionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionExpiryJob.class);

    // Consensus confirms in seconds; even a post-restart BBA recovery finishes
    // well within this window. Anything older is unrecoverable.
    private static final Duration EXPIRY = Duration.ofMinutes(15);

    private final TransactionCacheRepository transactionRepository;
    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;

    public PendingTransactionExpiryJob(
            TransactionCacheRepository transactionRepository,
            UserRepository userRepository,
            CampaignRepository campaignRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.campaignRepository = campaignRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireOrphanedPendingTransactions() {
        Instant cutoff = Instant.now().minus(EXPIRY);
        List<TransactionCache> orphans =
                transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.PENDING, cutoff);

        for (TransactionCache transaction : orphans) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            refundLockedMoney(transaction);
            log.warn("Expired orphaned PENDING transaction {} (type={}, amount={}, created={}) — "
                            + "no on-chain outcome received within {} minutes",
                    transaction.getId(), transaction.getType(), transaction.getAmount(),
                    transaction.getCreatedAt(), EXPIRY.toMinutes());
        }
    }

    // Mirror of OnChainEventService.refundLockedMoney: undo the escrow that was
    // locked before publishing. DEPOSIT/REWARD lock nothing, so nothing to undo.
    private void refundLockedMoney(TransactionCache transaction) {
        AppUser user = transaction.getUser();
        BigDecimal amount = transaction.getAmount() == null ? BigDecimal.ZERO : transaction.getAmount();

        if (transaction.getType() == TransactionType.DONATE
                || transaction.getType() == TransactionType.STAKE
                || transaction.getType() == TransactionType.WITHDRAW) {
            user.setPendingBalance(safe(user.getPendingBalance()).subtract(amount));
            user.setAvailableBalance(safe(user.getAvailableBalance()).add(amount));
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.UNSTAKE) {
            user.setPendingBalance(safe(user.getPendingBalance()).subtract(amount));
            user.setStakedBalance(safe(user.getStakedBalance()).add(amount));
            userRepository.save(user);
        } else if (transaction.getType() == TransactionType.CREATE && transaction.getCampaign() != null) {
            Campaign campaign = transaction.getCampaign();
            campaign.setStatus(CampaignStatus.PENDING);
            campaignRepository.save(campaign);
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
