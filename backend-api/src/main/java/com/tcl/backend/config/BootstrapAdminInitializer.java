package com.tcl.backend.config;

import com.tcl.backend.users.Account;
import com.tcl.backend.users.AccountRepository;
import com.tcl.backend.users.AccountRole;
import com.tcl.backend.users.AdminProfile;
import com.tcl.backend.users.AdminRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/*
 * Creates the first admin account automatically when the backend starts.
 *
 * Why this exists:
 * - Admin-only APIs require an ADMIN account.
 * - On a fresh database, there may be no admin yet.
 * - This initializer creates one bootstrap admin from application.yml settings.
 *
 * This is not a controller. It is startup logic.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    // Whether bootstrap admin creation is enabled in application.yml.
    private final boolean enabled;
    // Admin username from application.yml.
    private final String username;
    // Admin email from application.yml. Used as login identity.
    private final String email;
    // Plain password from config; encoded before storing in database.
    private final String password;
    // Repository for accounts table.
    private final AccountRepository accountRepository;
    // Repository for admins table.
    private final AdminRepository adminRepository;
    // BCrypt encoder from SecurityConfig.
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            /*
             * @Value reads one config value from application.yml.
             *
             * Example:
             * app.bootstrap-admin.enabled
             */
            @Value("${app.bootstrap-admin.enabled}") boolean enabled,
            @Value("${app.bootstrap-admin.username}") String username,
            @Value("${app.bootstrap-admin.email}") String email,
            @Value("${app.bootstrap-admin.password}") String password,
            AccountRepository accountRepository,
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.enabled = enabled;
        this.username = username;
        this.email = email;
        this.password = password;
        this.accountRepository = accountRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /*
     * ApplicationRunner means Spring Boot calls run(...) once after the application
     * has started and all beans are ready.
     *
     * args contains command-line arguments passed to the app. We do not need them
     * here, but ApplicationRunner requires this parameter.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        /*
         * Do nothing if:
         * - bootstrap admin is disabled.
         *
         * The account/admin existence checks below prevent duplicates on restart.
         */
        if (!enabled) {
            return;
        }

        String normalizedEmail = email.toLowerCase();
        Account savedAccount = accountRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> createAdminAccount(normalizedEmail));

        if (adminRepository.existsById(savedAccount.getId())) {
            return;
        }

        if (savedAccount.getRole() != AccountRole.ADMIN) {
            return;
        }

        createAdminProfile(savedAccount);
    }

    private Account createAdminAccount(String normalizedEmail) {
        /*
         * Create the ACCOUNTS row.
         *
         * Admins and users both start as accounts. Role decides whether Spring
         * Security treats the account as ADMIN or USER.
         */
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setUsername(username);
        account.setEmail(normalizedEmail);
        // Store BCrypt hash, never the plain password.
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(AccountRole.ADMIN);
        /*
         * save(...) returns the managed Account instance.
         *
         * For shared-primary-key entities like AdminProfile, using the managed
         * Account avoids Hibernate trying to save the admin row with an unresolved
         * account_id.
         */
        return accountRepository.save(account);
    }

    private void createAdminProfile(Account savedAccount) {
        /*
         * Create the ADMINS row.
         *
         * In the ERD, admins.account_id is also a foreign key to accounts.id.
         * Passing the Account object lets JPA/Hibernate write that account_id link.
         */
        AdminProfile admin = new AdminProfile();
        admin.setAccount(savedAccount);
        adminRepository.save(admin);
    }
}
