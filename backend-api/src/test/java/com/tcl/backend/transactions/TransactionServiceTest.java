package com.tcl.backend.transactions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tcl.backend.campaigns.CampaignRepository;
import com.tcl.backend.common.ApiException;
import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.messaging.MempoolPublisher;
import com.tcl.backend.messaging.SystemKeySigner;
import com.tcl.backend.security.AuthenticatedAccount;
import com.tcl.backend.users.Account;
import com.tcl.backend.users.AccountRole;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionServiceTest {

    private final TransactionCacheRepository transactionRepository = mock(TransactionCacheRepository.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MempoolPublisher mempoolPublisher = mock(MempoolPublisher.class);
    private final TransactionService service = new TransactionService(
            transactionRepository,
            campaignRepository,
            userRepository,
            mempoolPublisher,
            new SystemKeySigner(),
            new MoneyFormatter()
    );

    @Test
    void stakeRejectsInsufficientAvailableBalanceBeforePublishing() {
        AuthenticatedAccount principal = principal();
        AppUser user = user(principal, new BigDecimal("10.00"), BigDecimal.ZERO, false);
        when(userRepository.findById(principal.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.stake(new AmountRequest(new BigDecimal("20.00")), principal))
                .isInstanceOf(ApiException.class)
                .hasMessage("Insufficient available balance");
        verifyNoInteractions(mempoolPublisher);
    }

    @Test
    void withdrawRejectsInsufficientAvailableBalanceBeforePublishing() {
        AuthenticatedAccount principal = principal();
        AppUser user = user(principal, new BigDecimal("10.00"), BigDecimal.ZERO, false);
        when(userRepository.findById(principal.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.withdraw(new AmountRequest(new BigDecimal("20.00")), principal))
                .isInstanceOf(ApiException.class)
                .hasMessage("Insufficient available balance");
        verifyNoInteractions(mempoolPublisher);
    }

    @Test
    void unstakeRejectsActiveValidatorBeforePublishing() {
        AuthenticatedAccount principal = principal();
        AppUser user = user(principal, new BigDecimal("10.00"), new BigDecimal("20.00"), true);
        when(userRepository.findById(principal.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.unstake(new AmountRequest(new BigDecimal("5.00")), principal))
                .isInstanceOf(ApiException.class)
                .hasMessage("Cannot unstake while validator mode is active");
        verifyNoInteractions(mempoolPublisher);
    }

    private AuthenticatedAccount principal() {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail("user@example.com");
        account.setUsername("user");
        account.setPasswordHash("hash");
        account.setRole(AccountRole.USER);
        return new AuthenticatedAccount(account);
    }

    private AppUser user(AuthenticatedAccount principal, BigDecimal available, BigDecimal staked, boolean activeValidator) {
        AppUser user = new AppUser();
        user.setAccount(principal.getAccount());
        user.setWalletAddress("0xabc");
        user.setAvailableBalance(available);
        user.setStakedBalance(staked);
        user.setActiveValidator(activeValidator);
        return user;
    }
}
