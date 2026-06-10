package com.tcl.backend.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Repository for reading/writing Account rows in the accounts table.
// Spring creates the runtime implementation automatically; we only define the interface.
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Spring Data reads this method name as:
    // "find one Account where the email field equals the given email".
    // It returns Optional because an email may not exist in the database.
    Optional<Account> findByEmail(String email);

    // Spring Data reads this as:
    // "return true if any Account exists with this email".
    // Used during registration to reject duplicate emails before saving.
    boolean existsByEmail(String email);
}
