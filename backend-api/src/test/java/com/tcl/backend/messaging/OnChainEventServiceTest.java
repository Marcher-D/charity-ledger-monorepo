package com.tcl.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tcl.backend.campaigns.Campaign;
import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.campaigns.CampaignStatus;
import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.transactions.TransactionCacheRepository;
import com.tcl.backend.transactions.TransactionStatus;
import com.tcl.backend.transactions.TransactionType;
import com.tcl.backend.users.Account;
import com.tcl.backend.users.AccountRole;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnChainEventServiceTest {

    private final TransactionCacheRepository transactionRepository = mock(TransactionCacheRepository.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OnChainEventService service = new OnChainEventService(
            transactionRepository,
            campaignRepository,
            userRepository
    );

    @Test
    void successfulDonateUpdatesTransactionCampaignAndUser() {
        AppUser user = user(new BigDecimal("80.00"), new BigDecimal("20.00"), BigDecimal.ZERO);
        Campaign campaign = campaign(user);
        TransactionCache transaction = transaction(TransactionType.DONATE, user, campaign, new BigDecimal("20.00"));
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.BLOCK_SUCCESS,
                System.currentTimeMillis(),
                Map.of("successfulTxIds", List.of(transaction.getId().toString()))
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
    void successfulCreateOpensCampaign() {
        AppUser user = user(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Campaign campaign = campaign(user);
        campaign.setStatus(CampaignStatus.PROCESSING);
        TransactionCache transaction = transaction(TransactionType.CREATE, user, campaign, BigDecimal.ZERO);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.BLOCK_SUCCESS,
                System.currentTimeMillis(),
                Map.of("successfulTxIds", List.of(transaction.getId()))
        ));

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.OPEN);
        assertThat(campaign.getOnChainAddress()).isEqualTo("0xcampaign");
    }

    @Test
    void failedStakeRefundsPendingBalance() {
        AppUser user = user(new BigDecimal("70.00"), new BigDecimal("30.00"), BigDecimal.ZERO);
        TransactionCache transaction = transaction(TransactionType.STAKE, user, null, new BigDecimal("30.00"));
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        service.apply(new EventBusMessage(
                UUID.randomUUID(),
                EventBusEventType.TX_EXPIRED,
                System.currentTimeMillis(),
                Map.of("txId", transaction.getId().toString(), "senderAddress", user.getWalletAddress())
        ));

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(user.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(user.getPendingBalance()).isEqualByComparingTo("0.00");
        verify(transactionRepository).save(transaction);
        verify(userRepository).save(user);
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
        user.setWalletAddress("0xuser");
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
        campaign.setOnChainAddress("0xcampaign");
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
        return transaction;
    }
}
