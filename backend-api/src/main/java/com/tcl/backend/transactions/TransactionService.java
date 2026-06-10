package com.tcl.backend.transactions;

import com.tcl.backend.campaigns.Campaign;
import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.campaigns.CampaignStatus;
import com.tcl.backend.common.ApiException;
import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.messaging.MempoolPublisher;
import com.tcl.backend.security.AuthenticatedAccount;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Service layer for user transaction actions.
// It validates off-chain rules, locks balances into pending_balance, creates
// transactions_cache rows, and publishes outbound messages to RabbitMQ mempool.
@Service
public class TransactionService {

    // Placeholder target address for staking transactions until on-chain team provides real address.
    private static final String STAKE_POOL_ADDRESS = "STAKE_POOL";
    // Placeholder target address for validator toggle transactions.
    private static final String VALIDATOR_REGISTRY_ADDRESS = "VALIDATOR_REGISTRY";

    // Database access for transactions_cache.
    private final TransactionCacheRepository transactionRepository;
    // Used to load donation target campaigns.
    private final CampaignRepository campaignRepository;
    // Used to load the logged-in user's AppUser profile/balances.
    private final UserRepository userRepository;
    // Publishes accepted transactions to RabbitMQ mempool_queue.
    private final MempoolPublisher mempoolPublisher;
    // Normalizes BigDecimal money values before storage/publishing.
    private final MoneyFormatter moneyFormatter;

    // Constructor injection: Spring provides all required beans.
    public TransactionService(
            TransactionCacheRepository transactionRepository,
            CampaignRepository campaignRepository,
            UserRepository userRepository,
            MempoolPublisher mempoolPublisher,
            MoneyFormatter moneyFormatter
    ) {
        this.transactionRepository = transactionRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.mempoolPublisher = mempoolPublisher;
        this.moneyFormatter = moneyFormatter;
    }

    // Read the logged-in user's transaction history.
    @Transactional(readOnly = true)
    public List<TransactionResponse> myTransactions(AuthenticatedAccount principal) {
        AppUser user = findUser(principal);
        // Newest transactions first.
        // stream/map/toList converts TransactionCache entities into TransactionResponse DTOs.
        return transactionRepository.findByUserAccountId(user.getAccountId(), Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    // User donates to an OPEN campaign.
    // Money is moved from available_balance to pending_balance before publishing.
    @Transactional
    public TransactionActionResponse donate(DonateRequest request, AuthenticatedAccount principal) {
        AppUser user = findUser(principal);
        // Donation must target an existing campaign.
        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campaign not found"));
        // Only OPEN campaigns can receive donations.
        if (campaign.getStatus() != CampaignStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Campaign is not open for donations");
        }
        // Normalize amount to fixed money scale, then check/lock funds.
        BigDecimal amount = moneyFormatter.normalize(request.amount());
        requireAvailableBalance(user, amount);
        lockAvailableBalance(user, amount);

        // Create PENDING cache row, publish to mempool, save txHash after publisher sets it.
        TransactionCache transaction = pending(TransactionType.DONATE, user, campaign, amount, request.note());
        mempoolPublisher.publish(transaction, user.getWalletAddress(), campaign.getOnChainAddress());
        transactionRepository.save(transaction);
        return accepted(transaction, "Donation is waiting for on-chain confirmation");
    }

    // User stakes available funds.
    // available_balance -> pending_balance now; Event Bus success later moves pending -> staked.
    @Transactional
    public TransactionActionResponse stake(AmountRequest request, AuthenticatedAccount principal) {
        AppUser user = findUser(principal);
        BigDecimal amount = moneyFormatter.normalize(request.amount());
        requireAvailableBalance(user, amount);
        lockAvailableBalance(user, amount);

        TransactionCache transaction = pending(TransactionType.STAKE, user, null, amount, "STAKE");
        mempoolPublisher.publish(transaction, user.getWalletAddress(), STAKE_POOL_ADDRESS);
        transactionRepository.save(transaction);
        return accepted(transaction, "Stake request is waiting for on-chain confirmation");
    }

    // User unstakes staked funds.
    // staked_balance -> pending_balance now; Event Bus success later moves pending -> available.
    @Transactional
    public TransactionActionResponse unstake(AmountRequest request, AuthenticatedAccount principal) {
        AppUser user = findUser(principal);
        // Active validators must opt out before unstaking.
        if (user.isActiveValidator()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot unstake while validator mode is active");
        }
        BigDecimal amount = moneyFormatter.normalize(request.amount());
        if (user.getStakedBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient staked balance");
        }
        lockStakedBalance(user, amount);

        TransactionCache transaction = pending(TransactionType.UNSTAKE, user, null, amount, "UNSTAKE");
        mempoolPublisher.publish(transaction, user.getWalletAddress(), STAKE_POOL_ADDRESS);
        transactionRepository.save(transaction);
        return accepted(transaction, "Unstake request is waiting for on-chain confirmation");
    }

    // User asks on-chain validator registry to turn validator mode on or off.
    @Transactional
    public TransactionActionResponse toggleValidator(ValidatorToggleRequest request, AuthenticatedAccount principal) {
        AppUser user = findUser(principal);
        // Normalize action so "opt_in" and "OPT_IN" are handled the same.
        String action = request.action().trim().toUpperCase();
        if (!action.equals("OPT_IN") && !action.equals("OPT_OUT")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validator action must be OPT_IN or OPT_OUT");
        }
        // A user must have stake before becoming an active validator.
        if (action.equals("OPT_IN") && user.getStakedBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stake balance is required before enabling validator mode");
        }

        // Amount is zero because this transaction changes validator status, not balance.
        // The action text is stored in note so Event Bus success can infer active/inactive.
        TransactionCache transaction = pending(TransactionType.VALIDATOR_TOGGLE, user, null, BigDecimal.ZERO, action);
        mempoolPublisher.publish(transaction, user.getWalletAddress(), VALIDATOR_REGISTRY_ADDRESS);
        transactionRepository.save(transaction);
        return accepted(transaction, "Validator toggle is waiting for on-chain confirmation");
    }

    // Create and immediately flush a PENDING transactions_cache row.
    // Flush matters because publisher needs transaction.id as txId before sending to mempool.
    private TransactionCache pending(TransactionType type, AppUser user, Campaign campaign, BigDecimal amount, String note) {
        TransactionCache transaction = new TransactionCache();
        transaction.setType(type);
        transaction.setUser(user);
        transaction.setCampaign(campaign);
        transaction.setAmount(amount);
        transaction.setNote(note);
        transaction.setStatus(TransactionStatus.PENDING);
        transactionRepository.saveAndFlush(transaction);
        return transaction;
    }

    // Load AppUser profile for the logged-in account.
    // Spring Security proves login, but transaction actions require the users table row.
    private AppUser findUser(AuthenticatedAccount principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User profile is required"));
    }

    // Off-chain validation: user must have enough spendable money before we lock it.
    private void requireAvailableBalance(AppUser user, BigDecimal amount) {
        if (user.getAvailableBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient available balance");
        }
    }

    // Escrow/locked-state rule from update logs:
    // available_balance decreases and pending_balance increases while waiting for Event Bus.
    private void lockAvailableBalance(AppUser user, BigDecimal amount) {
        user.setAvailableBalance(balance(user.getAvailableBalance()).subtract(amount));
        user.setPendingBalance(balance(user.getPendingBalance()).add(amount));
        userRepository.save(user);
    }

    // For unstake, lock stake instead of spendable balance.
    // If on-chain fails/expires, Event Bus logic returns pending back to staked_balance.
    private void lockStakedBalance(AppUser user, BigDecimal amount) {
        user.setStakedBalance(balance(user.getStakedBalance()).subtract(amount));
        user.setPendingBalance(balance(user.getPendingBalance()).add(amount));
        userRepository.save(user);
    }

    // Null-safe balance helper. Database columns are NOT NULL, but this keeps service math defensive.
    private BigDecimal balance(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // Small helper for consistent "accepted and pending" API responses.
    private TransactionActionResponse accepted(TransactionCache transaction, String message) {
        return new TransactionActionResponse(transaction.getId(), transaction.getTxHash(), transaction.getStatus(), message);
    }
}
