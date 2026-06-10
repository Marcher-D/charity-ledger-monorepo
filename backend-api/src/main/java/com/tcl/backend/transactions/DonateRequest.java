package com.tcl.backend.transactions;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

// Request DTO for POST /api/v1/transactions/donate.
// Donate needs a campaign id, an amount, and an optional donor note.
public record DonateRequest(
        // Campaign receiving the donation.
        @NotNull UUID campaignId,
        // Donation amount must exist and must be at least 0.01.
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        // Optional donor message. Limited to match transactions_cache.note size.
        @Size(max = 500) String note
) {
}
