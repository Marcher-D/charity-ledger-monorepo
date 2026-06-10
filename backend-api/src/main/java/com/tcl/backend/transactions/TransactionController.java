package com.tcl.backend.transactions;

import com.tcl.backend.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// REST controller for transaction APIs.
// SecurityConfig requires authentication for all routes here because they are not public.
@RestController
// Every route in this controller starts with /api/v1/transactions.
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    // Service containing transaction business logic.
    private final TransactionService transactionService;

    // Constructor injection: Spring provides the TransactionService bean.
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Handles POST /api/v1/transactions/donate.
    @PostMapping("/donate")
    TransactionActionResponse donate(
            // JSON body becomes DonateRequest, then validation annotations run.
            @Valid @RequestBody DonateRequest request,
            // Current logged-in account from JWT/Spring Security.
            @AuthenticationPrincipal AuthenticatedAccount principal
    ) {
        return transactionService.donate(request, principal);
    }

    // Handles POST /api/v1/transactions/stake.
    @PostMapping("/stake")
    TransactionActionResponse stake(
            // Stake only needs an amount.
            @Valid @RequestBody AmountRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal
    ) {
        return transactionService.stake(request, principal);
    }

    // Handles POST /api/v1/transactions/unstake.
    @PostMapping("/unstake")
    TransactionActionResponse unstake(
            // Unstake also only needs an amount.
            @Valid @RequestBody AmountRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal
    ) {
        return transactionService.unstake(request, principal);
    }

    // Handles POST /api/v1/transactions/validator/toggle.
    @PostMapping("/validator/toggle")
    TransactionActionResponse toggleValidator(
            // Request body contains action: OPT_IN or OPT_OUT.
            @Valid @RequestBody ValidatorToggleRequest request,
            @AuthenticationPrincipal AuthenticatedAccount principal
    ) {
        return transactionService.toggleValidator(request, principal);
    }

    // Handles GET /api/v1/transactions/me.
    // Returns current user's transaction history.
    @GetMapping("/me")
    List<TransactionResponse> myTransactions(@AuthenticationPrincipal AuthenticatedAccount principal) {
        return transactionService.myTransactions(principal);
    }
}
