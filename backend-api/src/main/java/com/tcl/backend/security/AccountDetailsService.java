package com.tcl.backend.security;

import com.tcl.backend.users.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Spring Security calls a UserDetailsService when it needs to load a user for login.
// This implementation loads our Account row by email, then wraps it as AuthenticatedAccount.
@Service
public class AccountDetailsService implements UserDetailsService {

    // Repository for querying the accounts table.
    private final AccountRepository accountRepository;

    // Constructor injection: Spring provides the AccountRepository bean.
    public AccountDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    // Spring Security calls this method with the login username.
    // In this project, the login username is the user's email address.
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return accountRepository.findByEmail(email)
                // If Account exists, convert Account -> AuthenticatedAccount.
                // Since findByEmail returns Optional<Account>, map changes it into
                // Optional<AuthenticatedAccount>. If the Optional is empty, map does nothing.
                // AuthenticatedAccount::new is method-reference syntax.
                // It is the shorter version of: account -> new AuthenticatedAccount(account).
                .map(AuthenticatedAccount::new)
                // If no Account exists, tell Spring Security login cannot continue.
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
}
