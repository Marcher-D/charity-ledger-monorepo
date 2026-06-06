package com.tcl.backend.transactions;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// Request DTO for transaction APIs that only need an amount.
// Used by stake and unstake endpoints.
public record AmountRequest(
        // Amount must exist and must be at least 0.01.
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {
}
