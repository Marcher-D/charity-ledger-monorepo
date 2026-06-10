package com.tcl.backend.auth;

import com.tcl.backend.common.ApiException;
import com.tcl.backend.messaging.NodeIdentity;
import com.tcl.backend.messaging.WalletService;
import com.tcl.backend.security.AuthenticatedAccount;
import com.tcl.backend.security.JwtService;
import com.tcl.backend.users.Account;
import com.tcl.backend.users.AccountRepository;
import com.tcl.backend.users.AccountRole;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Service layer for authentication.
// It handles registration, login, password hashing, wallet creation, and JWT issuing.
@Service
public class AuthService {

    // Database access for accounts table.
    private final AccountRepository accountRepository;
    // Database access for users table.
    private final UserRepository userRepository;
    // Hashes raw passwords and verifies password hashes through Spring Security.
    private final PasswordEncoder passwordEncoder;
    // Standard Spring Security login checker for email/password authentication.
    private final AuthenticationManager authenticationManager;
    // Creates JWT access tokens after successful register/login.
    private final JwtService jwtService;
    // Requests wallet addresses from the internal on-chain wallet service.
    private final WalletService walletService;

    // Constructor injection: Spring provides all required beans.
    public AuthService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            WalletService walletService
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.walletService = walletService;
    }

    // Transactional because registration writes two related rows:
    // accounts row + users row. If one save fails, both should roll back.
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Normalize email so Khoi@Email.com and khoi@email.com are treated as the same account.
        String email = request.email().trim().toLowerCase();
        if (accountRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        // Create login identity row.
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setUsername(request.username().trim());
        account.setEmail(email);
        // Store BCrypt hash, never the raw password.
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        // Public registration creates normal users, not admins.
        account.setRole(AccountRole.USER);
        /*
         * save(...) returns the managed Account instance.
         *
         * AppUser uses @MapsId, so its primary key comes from account.id.
         * Using the managed Account keeps the shared account_id mapping reliable.
         */
        Account savedAccount = accountRepository.save(account);

        /*
         * Spawn this user's dedicated blockchain node through the NodeOrchestrator
         * and persist its identity. The node holds its own private key; the backend
         * only stores the address, public key and node index.
         *
         * Like all on-chain accounts after the 5 genesis validators, the new node
         * starts with 0 balance and 0 stake — the user must DEPOSIT before they
         * can donate or stake.
         */
        NodeIdentity node = walletService.provisionUserNode(savedAccount.getId());

        // Create user wallet/balance profile linked to the account.
        AppUser user = new AppUser();
        user.setAccount(savedAccount);
        user.setWalletAddress(node.address());
        user.setPublicKey(node.publicKey());
        user.setNodeIndex(node.nodeIndex());
        user.setAvailableBalance(BigDecimal.ZERO);
        user.setPendingBalance(BigDecimal.ZERO);
        user.setStakedBalance(BigDecimal.ZERO);
        userRepository.save(user);

        return tokenFor(savedAccount);
    }

    // Login checks email/password using Spring Security, then returns a JWT.
    // No @Transactional is required here because login does not write database state.
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        // AuthenticationManager delegates to DaoAuthenticationProvider, which loads account
        // by email and compares the raw password with the stored BCrypt hash.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return tokenFor(account);
    }

    // Shared helper used by both register and login.
    private AuthResponse tokenFor(Account account) {
        // JwtService expects AuthenticatedAccount because that is our UserDetails adapter.
        String token = jwtService.generateToken(new AuthenticatedAccount(account));
        return new AuthResponse(token, "Bearer", jwtService.getExpiresInSeconds());
    }
}
