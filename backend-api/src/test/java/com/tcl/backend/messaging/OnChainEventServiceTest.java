package com.tcl.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tcl.backend.campaigns.Campaign;
import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.campaigns.CampaignStatus;
import com.tcl.backend.onchain.ProcessedReward;
import com.tcl.backend.onchain.ProcessedRewardRepository;
import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.transactions.TransactionCacheRepository;
import com.tcl.backend.transactions.TransactionStatus;
import com.tcl.backend.transactions.TransactionType;
import com.tcl.backend.users.Account;
import com.tcl.backend.users.AccountRole;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnChainEventServiceTest {

    private final TransactionCacheRepository transactionRepository = mock(TransactionCacheRepository.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProcessedRewardRepository processedRewardRepository = mock(ProcessedRewardRepository.class);
    private final OnChainEventService service = new OnChainEventService(
            transactionRepository,
            campaignRepository,
            userRepository,
            processedRewardRepository
    );

    @Test
    void validatedDonateUpdatesTransactionCampaignAndUser() {
        AppUser user = user(new BigDecimal("80.00"), new BigDecimal("20.00"), BigDecimal.ZERO);
        Campaign campaign = campaign(user);
        TransactionCache transaction = transaction(TransactionType.DONATE, user, campaign, new BigDecimal("20.00"));
        when(transactionRepository.findByTxHash(transaction.getTxHash())).thenReturn(Optional.of(transaction));

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.TXN_VALIDATED,
                System.currentTimeMillis(),
                Map.of("txId", transaction.getTxHash(), "type", "DONATE", "amount", 20.0)
        ));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(campaign.getCurrentRaised()).isEqualByComparingTo("20.00");
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("80.00");
        assertThat(user.getPendingBalance()).isEqualByComparingTo("0.00");
        verify(transactionRepository).save(transaction);
        verify(campaignRepository).save(campaign);
        verify(userRepository).save(user);
    }

    @Test
    void validatedDepositCreditsAvailableBalance() {
        AppUser user = user(new BigDecimal("0.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        TransactionCache transaction = transaction(TransactionType.DEPOSIT, user, null, new BigDecimal("100.00"));
        when(transactionRepository.findByTxHash(transaction.getTxHash())).thenReturn(Optional.of(transaction));

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.TXN_VALIDATED,
                System.currentTimeMillis(),
                Map.of("txId", transaction.getTxHash(), "type", "DEPOSIT", "amount", 100.0)
        ));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectedStakeRefundsPendingBalance() {
        AppUser user = user(new BigDecimal("70.00"), new BigDecimal("30.00"), BigDecimal.ZERO);
        TransactionCache transaction = transaction(TransactionType.STAKE, user, null, new BigDecimal("30.00"));
        when(transactionRepository.findByTxHash(transaction.getTxHash())).thenReturn(Optional.of(transaction));

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.TXN_REJECTED,
                System.currentTimeMillis(),
                Map.of("txId", transaction.getTxHash(), "type", "STAKE", "reason", "NETWORK_TIMEOUT")
        ));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(user.getPendingBalance()).isEqualByComparingTo("0.00");
        verify(transactionRepository).save(transaction);
        verify(userRepository).save(user);
    }

    @Test
    void blockFinalizedCreditsProposerRewardOnce() {
        AppUser proposer = user(new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("50.00"));
        when(processedRewardRepository.existsById("hash-1")).thenReturn(false);
        when(userRepository.findByWalletAddress(proposer.getWalletAddress())).thenReturn(Optional.of(proposer));

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.BLOCK_FINALIZED,
                System.currentTimeMillis(),
                Map.of(
                        "round", 4,
                        "blockHash", "hash-1",
                        "transactionsIncluded", 10,
                        "rewardAmount", 4.5,
                        "proposer", Map.of("publicKey", "pk", "address", proposer.getWalletAddress())
                )
        ));

        assertThat(proposer.getAvailableBalance()).isEqualByComparingTo("14.50");
        verify(processedRewardRepository).save(any(ProcessedReward.class));
        verify(userRepository).save(proposer);
        // A SUCCESS REWARD history row is created.
        verify(transactionRepository).save(any(TransactionCache.class));
    }

    @Test
    void duplicateBlockFinalizedIsSkipped() {
        // A second copy of the same reward event (other blockchain nodes publish
        // it too) must not credit the proposer again.
        when(processedRewardRepository.existsById("hash-1")).thenReturn(true);

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.BLOCK_FINALIZED,
                System.currentTimeMillis(),
                Map.of(
                        "round", 4,
                        "blockHash", "hash-1",
                        "rewardAmount", 4.5,
                        "proposer", Map.of("address", "someaddress")
                )
        ));

        verify(userRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void blockFinalizedForGenesisProposerIsLoggedAndSkipped() {
        when(processedRewardRepository.existsById("hash-2")).thenReturn(false);
        when(userRepository.findByWalletAddress("genesisaddr")).thenReturn(Optional.empty());

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.BLOCK_FINALIZED,
                System.currentTimeMillis(),
                Map.of(
                        "round", 7,
                        "blockHash", "hash-2",
                        "rewardAmount", 2.0,
                        "proposer", Map.of("address", "genesisaddr")
                )
        ));

        // Marker is still written so retries don't loop, but nothing is credited.
        verify(processedRewardRepository).save(any(ProcessedReward.class));
        verify(userRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    private AppUser user(BigDecimal available, BigDecimal pending, BigDecimal staked) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("user@example.com");
        account.setUsername("user");
        account.setPasswordHash("hash");
        account.setRole(AccountRole.USER);

        AppUser user = new AppUser();
        user.setAccount(account);
        user.setWalletAddress("useraddress");
        user.setAvailableBalance(available);
        user.setPendingBalance(pending);
        user.setStakedBalance(staked);
        return user;
    }

    private Campaign campaign(AppUser user) {
        Campaign campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setTitle("Bridge");
        campaign.setDescription("Build a bridge");
        campaign.setTargetAmount(new BigDecimal("100.00"));
        campaign.setCurrentRaised(BigDecimal.ZERO);
        campaign.setStatus(CampaignStatus.OPEN);
        campaign.setOnChainAddress("campaignaddress");
        campaign.setPublicKey("campaignPubKey");
        campaign.setCreatedBy(user);
        return campaign;
    }

    private TransactionCache transaction(TransactionType type, AppUser user, Campaign campaign, BigDecimal amount) {
        TransactionCache transaction = new TransactionCache();
        transaction.setId(UUID.randomUUID());
        transaction.setType(type);
        transaction.setUser(user);
        transaction.setCampaign(campaign);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setTxHash("txhash-" + transaction.getId());
        return transaction;
    }
}
