package com.tcl.backend.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByAccountEmail(String email);

    Optional<AppUser> findByWalletAddress(String walletAddress);
}
