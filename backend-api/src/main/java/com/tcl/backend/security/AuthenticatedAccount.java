package com.tcl.backend.security;

import com.tcl.backend.users.Account;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Spring Security works with UserDetails objects during login/JWT authentication.
// This adapter wraps our own Account entity in the shape Spring Security expects.
public class AuthenticatedAccount implements UserDetails {

    // The real database account row for the logged-in caller.
    private final Account account;

    public AuthenticatedAccount(Account account) {
        this.account = account;
    }

    // Convenience method for code that needs the account id.
    public UUID getId() {
        return account.getId();
    }

    // Gives services/controllers access to the full Account entity.
    public Account getAccount() {
        return account;
    }

    @Override
    // Authorities are permissions/roles used by Spring Security.
    // Spring's hasRole("ADMIN") check expects the stored authority to be ROLE_ADMIN.
    // Collection<? extends GrantedAuthority> means:
    // a collection whose items are GrantedAuthority objects or any compatible implementation.
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
    }

    @Override
    // Spring Security calls this during password authentication.
    // It must return the stored password hash, not the raw password.
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    // Spring Security's "username" field is our login email.
    public String getUsername() {
        return account.getEmail();
    }
}
