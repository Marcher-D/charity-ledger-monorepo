package com.tcl.backend.users;

import com.tcl.backend.security.AuthenticatedAccount;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Service layer for user profile operations.
// Controllers call this class instead of talking to repositories directly.
@Service
public class UserService {

    // Repository used to load rows from the users table.
    private final UserRepository userRepository;

    // Constructor injection: Spring passes the UserRepository bean here.
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // readOnly=true because this method only reads data and does not update the database.
    @Transactional(readOnly = true)
    public UserProfileResponse me(AuthenticatedAccount principal) {
        // principal is the authenticated account from the JWT/security layer.
        Account account = principal.getAccount();

        // Find the matching user profile by account id.
        // If found, combine account fields + user wallet/balance fields into one response DTO.
        return userRepository.findById(account.getId())
                .map(user -> new UserProfileResponse(
                        account.getId(),
                        account.getUsername(),
                        account.getEmail(),
                        account.getRole(),
                        user.getWalletAddress(),
                        user.getAvailableBalance(),
                        user.getPendingBalance(),
                        user.getStakedBalance(),
                        user.isActiveValidator()
                ))
                // If the account has no users row, return account info with empty wallet/balance values.
                // This can happen for ADMIN accounts because admins live in admins, not users.
                .orElseGet(() -> new UserProfileResponse(
                        account.getId(),
                        account.getUsername(),
                        account.getEmail(),
                        account.getRole(),
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false
                ));
    }
}
