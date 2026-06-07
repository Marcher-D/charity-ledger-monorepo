package com.tcl.backend.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<AdminProfile, UUID> {

    Optional<AdminProfile> findByAccountEmail(String email);
}
