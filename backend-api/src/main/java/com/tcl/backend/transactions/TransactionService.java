package com.tcl.backend.transactions;

import com.tcl.backend.campaigns.Campaign;
import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.campaigns.CampaignStatus;
import com.tcl.backend.common.ApiException;
import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.messaging.MempoolPublisher;
import com.tcl.backend.messaging.SystemKeySigner;
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
// transactions_cache rows, and publishes signed on-chain transactions to the
// RabbitMQ mempool. Settlement happens later when the blockchain's Event Bus
// reports TXN_VALIDATED / TXN_REJECTED for the published txId (stored as txHash).
@Service
public class TransactionService {

    // Database access for transactions_cache.
    private final TransactionCacheRepository transactionRepository;
    // Used to load donation target campaigns.
    private final CampaignRepository campaignRepository;
    // Used to load the logged-in user's AppUser profile/balances.
    private final UserRepository userRepository;
    // Signs (via sender node / system key) and publishes to RabbitMQ mempool_queue.
    private final MempoolPublisher mempoolPublisher;
    // Provides the system public key: WITHDRAW transactions send funds to it.
    private final SystemKeySigner systemKeySigner;
    // Normalizes BigDecimal money values before storage/publishing.
    private final MoneyFormatter moneyFormatter;

    // Constructor injection: Spring provides all required beans.
    public TransactionService(
            TransactionCacheRepository transactionRepository,
            CampaignRepository campaignRepository,
            UserRepository userRepository,
            MempoolPublisher mempoolPublisher,
            SystemKeySigner systemKeySigner,
            MoneyFormatter moneyFormatter
    ) {
        this.transactionRepository = transactionRepository;
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.mempoolPublisher = mempoolPublisher;
        this.systemKeySigner = systemKeySigner;
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

    // User deposits money into their on-chain account (faucet/fiat on-ramp).
    // The backend signs a DEPOSIT with the chain's system key (the only key
    // allowed to author DEPOSIT transactions); the user's balance is credited
    // when the Event Bus confirms TXN_VALIDATED.
    @Transactional
    public TransactionActionResponse deposit(AmountRequest request, AuthenticatedAccount principal) {
        AppUser user = findUser(principal);
        BigDecimal amount = moneyFormatter.normalize(request.amount());

        // Nothing is locked: the user gains money on success, nothing on failure.
        TransactionCache transaction = pending(TransactionType.DEPOSIT, user, null, amount, "DEPOSIT");
        mempoolPublisher.publishSystemSigned(transaction, requirePublicKey(user));
        transactionRepository.save(transaction);
        return accepted(transaction, "Deposit is waiting for on-chain confirmation");
    }

    // User withdraws money out of their on-chain account (off-ramp).
    // On-chain WITHDRAW is signed by the user's own node and sends the funds to
    // the system key's account (mirror of DEPOSIT, per the integration design).
    // available_balance -> pending_balance now; on TXN_VALIDATED the pending
    // amount is released and the money has left the system.
    @Transactional
    public TransactionActionResponse withdraw(AmountRequest request, AuthenticatedAccount principal) {
        AppUser user = findUser(principal);
        BigDecimal amount = moneyFormatter.normalize(request.amount());
        requireAvailableBalance(user, amount);
        lockAvailableBalance(user, amount);

        TransactionCache transaction = pending(TransactionType.WITHDRAW, user, null, amount, "WITHDRAW");
        mempoolPublisher.publishUserSigned(transaction, requireNodeIndex(user), systemKeySigner.publicKey());
        transactionRepository.save(transaction);
        return accepted(transaction, "Withdrawal is waiting for on-chain confirmation");
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
        // The campaign's node public key is the on-chain DONATE receiver.
        if (campaign.getPublicKey() == null || campaign.getPublicKey().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "Campaign has no on-chain identity yet");
        }
        // Normalize amount to fixed money scale, then check/lock funds.
        BigDecimal amount = moneyFormatter.normalize(request.amount());
        requireAvailableBalance(user, amount);
        lockAvailableBalance(user, amount);

        // Create PENDING cache row, sign with the user's own node, publish to mempool.
        TransactionCache transaction = pending(TransactionType.DONATE, user, campaign, amount, request.note());
        mempoolPublisher.publishUserSigned(transaction, requireNodeIndex(user), campaign.getPublicKey());
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

        // On-chain STAKE has no receiver: it moves the sender's balance into stake.
        TransactionCache transaction = pending(TransactionType.STAKE, user, null, amount, "STAKE");
        mempoolPublisher.publishUserSigned(transaction, requireNodeIndex(user), null);
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

        // On-chain UNSTAKE has no receiver: it returns the sender's stake to balance.
        TransactionCache transaction = pending(TransactionType.UNSTAKE, user, null, amount, "UNSTAKE");
        mempoolPublisher.publishUserSigned(transaction, requireNodeIndex(user), null);
        transactionRepository.save(transaction);
        return accepted(transaction, "Unstake request is waiting for on-chain confirmation");
    }

    // User toggles validator mode for their web profile.
    // This settles off-chain immediately: the chain has no VALIDATOR_TOGGLE
    // transaction type — on-chain validator status follows automatically from
    // stake (stake > 0 activates the node as a validator, stake == 0 deactivates).
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

        // Apply the toggle now and keep a SUCCESS history row; nothing is published.
        user.setActiveValidator(action.equals("OPT_IN"));
        userRepository.save(user);

        TransactionCache transaction = pending(TransactionType.VALIDATOR_TOGGLE, user, null, BigDecimal.ZERO, action);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);
        return accepted(transaction, "Validator mode is now " + (user.isActiveValidator() ? "active" : "inactive"));
    }

    // Create and immediately flush a PENDING transactions_cache row.
    // Flush matters because the row must exist before publishing to mempool.
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

    // The user's node index selects which node's signing service signs the transaction.
    private int requireNodeIndex(AppUser user) {
        if (user.getNodeIndex() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "User has no on-chain node yet");
        }
        return user.getNodeIndex();
    }

    // The user's node public key is the on-chain receiver of system DEPOSITs.
    private String requirePublicKey(AppUser user) {
        if (user.getPublicKey() == null || user.getPublicKey().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "User has no on-chain identity yet");
        }
        return user.getPublicKey();
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
